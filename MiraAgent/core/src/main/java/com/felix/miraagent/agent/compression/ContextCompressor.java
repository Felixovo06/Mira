package com.felix.miraagent.agent.compression;

import com.felix.miraagent.memory.SerializedMemoryWriter;
import com.felix.miraagent.model.Message;
import com.felix.miraagent.model.ModelClient;

import java.util.List;

public interface ContextCompressor {

    boolean shouldCompress(int lastRealInputTokens, CompressionPolicy policy);

    CompressResult compress(
            List<Message> conversationHistory,
            String sessionId,
            String userId,
            String characterId,
            CompressionPolicy policy,
            ModelClient modelClient,
            SerializedMemoryWriter memoryWriter
    );
}
