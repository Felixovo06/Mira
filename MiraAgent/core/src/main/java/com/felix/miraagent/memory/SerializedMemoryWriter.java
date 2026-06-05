package com.felix.miraagent.memory;

import java.util.concurrent.CompletableFuture;

/**
 * 串行化记忆写入器：所有写入/归档经单一后台 worker 顺序执行。
 *
 * <h3>持久化契约（实现须遵守）</h3>
 * <ul>
 *   <li><b>{@link #submit}</b>（同步，工具路径：用户明确要求记住）——<b>返回即落定</b>：
 *       文件与索引在返回前已写入,失败以 {@code success=false} 显式回传,绝不静默丢弃。
 *       背压下阻塞等待入队(有上限),超时按失败返回。</li>
 *   <li><b>{@link #submitAsync}</b>（异步,压缩/后台复盘等非关键路径：“压缩即学习”不应卡住主回复）——
 *       <b>尽力而为</b>：入队即返回；队满或停机中会被拒绝(future 以 {@code success=false} 完成),
 *       且硬崩溃会丢失尚未执行的入队项。事实源是 {@code .md} 文件,DB 索引为可重建投影,
 *       故丢失的索引可经 {@code MemoryIndexRebuildService.rebuild} 从文件恢复;
 *       但崩溃时连 {@code .md} 都还没写的异步项无法恢复——此为该路径的已知 SLA。</li>
 * </ul>
 *
 * <h3>并发不变量</h3>
 * 正确性依赖“全局唯一 worker 线程”：去重的 read-modify-write、{@code MemoryFileStore} 的
 * APPEND/归档读改写均<b>仅在单写者下安全</b>。多 worker / 多实例需另加 DB 约束或锁,不在本实现保证内。
 *
 * <h3>生命周期</h3>
 * {@link #shutdown} 会排空已入队任务后退出(带超时);调用方应在停止提交后再 shutdown——
 * 与 shutdown 并发的提交按“停机中”拒绝,语义同 {@code ExecutorService.shutdown}。
 */
public interface SerializedMemoryWriter {
    MemoryWriteResult submit(MemoryWriteRequest request);

    /**
     * 异步提交：入队后立即返回,不阻塞调用方。语义见类级“持久化契约”。
     * 默认退化为同步 submit,供测试替身复用。
     */
    default CompletableFuture<MemoryWriteResult> submitAsync(MemoryWriteRequest request) {
        return CompletableFuture.completedFuture(submit(request));
    }

    MemoryWriteResult archive(String userId, String memoryId);
    void shutdown();
}
