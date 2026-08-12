package com.sdvsync.mods.api

internal const val NEXUS_SEARCH_PAGE_SIZE = 50
internal const val NEXUS_MIN_SEARCH_QUERY_LENGTH = 2

internal data class NexusSearchOperation(val document: String, val variables: Map<String, Any>)

internal fun createNexusSearchOperation(searchQuery: String, page: Int = 1): NexusSearchOperation =
    NexusSearchOperation(
        document = """
            query SearchMods(${'$'}search: String!, ${'$'}gameId: String!, ${'$'}offset: Int!) {
              mods(
                filter: {
                  filter: [
                    { gameId: { value: ${'$'}gameId, op: EQUALS } }
                    { name: { value: ${'$'}search, op: WILDCARD } }
                  ]
                  op: AND
                }
                sort: { endorsements: { direction: DESC } }
                offset: ${'$'}offset
                count: $NEXUS_SEARCH_PAGE_SIZE
              ) {
                nodes {
                  modId
                  name
                  summary
                  author
                  pictureUrl
                  endorsementCount: endorsements
                  modDownloadCount: downloads
                  version
                  categoryName: category
                }
                totalCount
              }
            }
        """.trimIndent(),
        variables = mapOf(
            "search" to searchQuery,
            "gameId" to "1303",
            "offset" to (page.coerceAtLeast(1) - 1) * NEXUS_SEARCH_PAGE_SIZE
        )
    )
