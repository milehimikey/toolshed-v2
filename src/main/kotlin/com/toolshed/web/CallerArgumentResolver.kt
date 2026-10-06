package com.toolshed.web

import com.toolshed.axon.NeighborId
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * Resolves a [Caller] from the trusted sign-in headers. The sign-in layer in front of the app
 * sets them; the app never checks who the caller really is.
 *
 * A malformed neighbor id makes the whole request not signed in, so the caller gets no identity
 * at all, not even the role.
 */
class CallerArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == Caller::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Caller = resolveCaller(webRequest.getHeader(NEIGHBOR_HEADER), webRequest.getHeader(ROLE_HEADER))

    companion object {
        const val NEIGHBOR_HEADER = "X-Toolshed-Neighbor-Id"
        const val ROLE_HEADER = "X-Toolshed-Role"
        const val SHED_MANAGER_ROLE = "shed-manager"

        private val CANONICAL_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun resolveCaller(
            neighborHeader: String?,
            roleHeader: String?,
        ): Caller {
            val neighborValue = neighborHeader?.trim()?.takeIf { it.isNotEmpty() }
            if (neighborValue != null && !CANONICAL_UUID.matches(neighborValue)) return Caller.ANONYMOUS
            return Caller(
                neighborId = neighborValue?.let { NeighborId(UUID.fromString(it)) },
                isShedManager = roleHeader?.trim() == SHED_MANAGER_ROLE,
            )
        }
    }
}
