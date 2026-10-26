package com.toolshed.toolcatalog

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import org.springframework.stereotype.Component

/** Answers the catalog listing. No paging; the same rows for every neighbor (INV-TCAT-5). */
@Component
class ToolCatalogQueryHandler(
    private val repository: ToolCatalogRepository,
) {
    @QueryHandler
    fun handle(query: ListToolCatalog): ToolCatalog = ToolCatalog(repository.rows())
}
