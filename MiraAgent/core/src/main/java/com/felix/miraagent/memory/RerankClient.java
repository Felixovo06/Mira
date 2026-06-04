package com.felix.miraagent.memory;

import java.util.List;

/** 文本重排：对候选文档按与 query 的相关性打分。由检索器在融合之后调用，作为最终排序的主信号。 */
public interface RerankClient {

    /**
     * 对 documents 按与 query 的相关性重排。
     *
     * @return 与 documents 顺序对齐的相关性分数（越大越相关，约 [0,1]）；长度等于 documents.size()。
     */
    double[] rerank(String query, List<String> documents);
}
