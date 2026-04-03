import logging
import os
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

from mem0 import Memory

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

# ── Patch: fix Mem0's Anthropic tool format for current API ───────────
def _patch_anthropic_llm():
    try:
        from mem0.llms.anthropic import AnthropicLLM
        _orig = AnthropicLLM.generate_response

        def _patched(self, messages, response_format=None, tools=None, tool_choice="auto", **kwargs):
            # Convert OpenAI-style tools to Anthropic-native format
            if tools:
                converted = []
                for t in tools:
                    if t.get("type") == "function" and "function" in t:
                        fn = t["function"]
                        converted.append({
                            "name": fn["name"],
                            "description": fn.get("description", ""),
                            "input_schema": fn.get("parameters", {"type": "object", "properties": {}}),
                        })
                    else:
                        converted.append(t)
                tools = converted
            # Convert tool_choice string to dict
            if isinstance(tool_choice, str):
                tool_choice = {"type": tool_choice}

            # ── Inline the logic instead of calling _orig to control params ──
            system_message = ""
            filtered_messages = []
            for message in messages:
                if message["role"] == "system":
                    system_message = message["content"]
                else:
                    filtered_messages.append(message)

            params = {
                "model": self.config.model,
                "messages": filtered_messages,
                "system": system_message,
                "temperature": self.config.temperature,
                "max_tokens": self.config.max_tokens,
                # NOTE: top_p intentionally omitted — Anthropic rejects temp+top_p together
            }
            if tools:
                params["tools"] = tools
                params["tool_choice"] = tool_choice

            response = self.client.messages.create(**params)
            # If tools were provided, return OpenAI-style tool_calls dict
            # (Mem0's graph_memory expects response["tool_calls"][*]["name"] + ["arguments"])
            if tools:
                tool_calls = []
                for block in response.content:
                    if block.type == "tool_use":
                        tool_calls.append({
                            "name": block.name,
                            "arguments": block.input,
                        })
                # Always return dict when tools are expected (Mem0 calls .get() on result)
                return {"tool_calls": tool_calls} if tool_calls else {"tool_calls": []}
            # Regular text response
            for block in response.content:
                if hasattr(block, "text"):
                    return block.text
            return str(response.content[0])

        AnthropicLLM.generate_response = _patched
        logging.info("Patched AnthropicLLM for current Anthropic API format")
    except Exception as e:
        logging.warning(f"Could not patch AnthropicLLM: {e}")

_patch_anthropic_llm()
load_dotenv()

# ── Environment variables ──────────────────────────────────────────────
POSTGRES_HOST     = os.environ.get("POSTGRES_HOST", "mem0-pgvector")
POSTGRES_PORT     = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_DB       = os.environ.get("POSTGRES_DB", "postgres")
POSTGRES_USER     = os.environ.get("POSTGRES_USER", "postgres")
POSTGRES_PASSWORD = os.environ.get("MEM0_POSTGRES_PASSWORD", "mem0pass")

NEO4J_URI      = os.environ.get("NEO4J_URI", "bolt://mem0-neo4j:7687")
NEO4J_USERNAME = os.environ.get("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.environ.get("MEM0_NEO4J_PASSWORD", "mem0graph")

OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://ollama:11434")
EMBED_MODEL     = os.environ.get("EMBED_MODEL", "nomic-embed-text")

ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY", "")
LLM_MODEL         = os.environ.get("LLM_MODEL", "claude-haiku-4-5-20251001")

HISTORY_DB_PATH = os.environ.get("HISTORY_DB_PATH", "/app/history/history.db")

# ── Config: Claude Haiku for LLM (fast), Ollama for embeddings (free) ─
DEFAULT_CONFIG = {
    "version": "v1.1",
    "llm": {
        "provider": "anthropic",
        "config": {
            "model": LLM_MODEL,
            "api_key": ANTHROPIC_API_KEY,
            "temperature": 0.2,
        },
    },
    "embedder": {
        "provider": "ollama",
        "config": {
            "model": EMBED_MODEL,
            "ollama_base_url": OLLAMA_BASE_URL,
            "embedding_dims": 768,
        },
    },
    "vector_store": {
        "provider": "pgvector",
        "config": {
            "host": POSTGRES_HOST,
            "port": int(POSTGRES_PORT),
            "dbname": POSTGRES_DB,
            "user": POSTGRES_USER,
            "password": POSTGRES_PASSWORD,
            "collection_name": "memories",
            "embedding_model_dims": 768,
        },
    },
    "graph_store": {
        "provider": "neo4j",
        "config": {
            "url": NEO4J_URI,
            "username": NEO4J_USERNAME,
            "password": NEO4J_PASSWORD,
        },
    },
    "history_db_path": HISTORY_DB_PATH,
}

logging.info(f"Mem0 config: LLM={LLM_MODEL} via Anthropic, Embedder={EMBED_MODEL} via Ollama")
MEMORY_INSTANCE = Memory.from_config(DEFAULT_CONFIG)
logging.info("Mem0 Memory instance created successfully!")

app = FastAPI(
    title="Mem0 REST APIs",
    description="A REST API for managing and searching memories for your AI Agents and Apps.",
    version="1.0.0",
)


class Message(BaseModel):
    role: str = Field(..., description="Role of the message (user or assistant).")
    content: str = Field(..., description="Message content.")


class MemoryCreate(BaseModel):
    messages: List[Message] = Field(..., description="List of messages to store.")
    user_id: Optional[str] = None
    agent_id: Optional[str] = None
    run_id: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class SearchQuery(BaseModel):
    query: str = Field(..., description="Search query.")
    user_id: Optional[str] = None
    agent_id: Optional[str] = None
    run_id: Optional[str] = None
    limit: Optional[int] = 10


class MemoryUpdate(BaseModel):
    data: str = Field(..., description="Updated memory content.")


@app.get("/", response_class=RedirectResponse)
def root():
    return RedirectResponse(url="/docs")


@app.post("/v1/memories/", response_model=Dict)
def add_memory(memory: MemoryCreate):
    try:
        messages = [{"role": m.role, "content": m.content} for m in memory.messages]
        params = {"messages": messages}
        if memory.user_id:
            params["user_id"] = memory.user_id
        if memory.agent_id:
            params["agent_id"] = memory.agent_id
        if memory.run_id:
            params["run_id"] = memory.run_id
        if memory.metadata:
            params["metadata"] = memory.metadata
        result = MEMORY_INSTANCE.add(**params)
        return result
    except Exception as e:
        logging.error(f"Error adding memory: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/memories/search/")
def search_memories(query: SearchQuery):
    try:
        params = {"query": query.query}
        if query.user_id:
            params["user_id"] = query.user_id
        if query.agent_id:
            params["agent_id"] = query.agent_id
        if query.run_id:
            params["run_id"] = query.run_id
        if query.limit:
            params["limit"] = query.limit
        result = MEMORY_INSTANCE.search(**params)
        # Mem0 may return dict {"results": [...], "relations": [...]} or a flat list
        if isinstance(result, dict) and "results" in result:
            return result["results"]
        return result
    except Exception as e:
        logging.error(f"Error searching memories: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/memories/")
def get_memories(user_id: Optional[str] = None, agent_id: Optional[str] = None, run_id: Optional[str] = None):
    try:
        params = {}
        if user_id:
            params["user_id"] = user_id
        if agent_id:
            params["agent_id"] = agent_id
        if run_id:
            params["run_id"] = run_id
        result = MEMORY_INSTANCE.get_all(**params)
        if isinstance(result, dict) and "results" in result:
            return result["results"]
        return result
    except Exception as e:
        logging.error(f"Error getting memories: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/memories/{memory_id}/", response_model=Dict)
def get_memory(memory_id: str):
    try:
        result = MEMORY_INSTANCE.get(memory_id)
        return result
    except Exception as e:
        logging.error(f"Error getting memory: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.put("/v1/memories/{memory_id}/", response_model=Dict)
def update_memory(memory_id: str, memory: MemoryUpdate):
    try:
        result = MEMORY_INSTANCE.update(memory_id, memory.data)
        return result
    except Exception as e:
        logging.error(f"Error updating memory: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/v1/memories/{memory_id}/", response_model=Dict)
def delete_memory(memory_id: str):
    try:
        MEMORY_INSTANCE.delete(memory_id)
        return {"message": "Memory deleted successfully!"}
    except Exception as e:
        logging.error(f"Error deleting memory: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/v1/memories/", response_model=Dict)
def delete_all_memories(user_id: Optional[str] = None, agent_id: Optional[str] = None, run_id: Optional[str] = None):
    try:
        params = {}
        if user_id:
            params["user_id"] = user_id
        if agent_id:
            params["agent_id"] = agent_id
        if run_id:
            params["run_id"] = run_id
        MEMORY_INSTANCE.delete_all(**params)
        return {"message": "All memories deleted successfully!"}
    except Exception as e:
        logging.error(f"Error deleting memories: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/memories/{memory_id}/history/", response_model=List)
def get_memory_history(memory_id: str):
    try:
        result = MEMORY_INSTANCE.history(memory_id)
        return result
    except Exception as e:
        logging.error(f"Error getting memory history: {e}")
        raise HTTPException(status_code=500, detail=str(e))
