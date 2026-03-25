package com.destinyoracle.dto.response;

import java.util.List;

public record Mem0MemoryResponse(
    List<Memory> memories
) {
    public record Memory(
        String id,
        String memory,
        String hash,
        Double score
    ) {}
}
