package com.clang.session.repository.search

import com.clang.session.domain.User
import org.elasticsearch.index.query.QueryBuilders.queryStringQuery
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

/**
 * Spring Data Elasticsearch repository for the User entity.
 */
interface UserSearchRepository : ElasticsearchRepository<User, Long>, UserSearchRepositoryInternal

interface UserSearchRepositoryInternal {
    fun search(query: String): List<User>
}

class UserSearchRepositoryInternalImpl(private val elasticsearchTemplate: ElasticsearchRestTemplate) : UserSearchRepositoryInternal {

    override fun search(query: String): List<User> {
        val nativeSearchQuery = NativeSearchQuery(queryStringQuery(query))
        return elasticsearchTemplate
            .search(nativeSearchQuery, User::class.java)
            .map(SearchHit<User>::getContent)
            .toList()
    }
}
