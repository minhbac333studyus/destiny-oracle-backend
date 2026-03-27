import logging
import os
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

from mem0 import Memory

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
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
LLM_MODEL       = os.environ.get("LLM_MODEL", "qwen2.5:1.5b")
EMBED_MODEL     = os.environ.get("EMBED_MODEL", "nomic-embed-text")

HISTORY_DB_PATH = os.environ.get("HISTORY_DB_PATH", "/app/history/history.db")

# ── Config: Ollama for both LLM and embeddings ($0) ───────────────────
DEFAULT_CONFIG = {
    "version": "v1.1",
    "llm": {
        "provider": "ollama",
        "config": {
            "model": LLM_MODEL,
            "ollama_base_url": OLLAMA_BASE_URL,
            "temperature": 0.2,
        },
    },
    "embedder": {
        "provider": "ollama",
        "config": {
            "model": EMBED_MODEL,
            "ollama_base_url": OLLAMA_BASE_URL,
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

logging.info(f"Mem0 config: LLM={LLM_MODEL} via Ollama, Embedder={EMBED_MODEL} via Ollama")
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


@app.post("/v1/memories/search/", response_model=List)
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
        return result
    except Exception as e:
        logging.error(f"Error searching memories: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/memories/", response_model=List)
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
