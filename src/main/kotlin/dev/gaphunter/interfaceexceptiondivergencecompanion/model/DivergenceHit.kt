package dev.gaphunter.interfaceexceptiondivergencecompanion.model

import com.intellij.psi.PsiElement

/**
 * A confirmed Liskov-violation call site: a call through an INTERFACE-
 * typed reference to [interfaceName].[methodName], wrapped in a
 * broad `catch` that silently swallows it, where at least one real
 * implementation ([throwingImplementationName]) throws
 * [missingExceptionSimpleName] directly and at least one other real
 * implementation of the SAME interface method never does.
 */
data class DivergenceHit(
    val anchor: PsiElement,
    val interfaceName: String,
    val methodName: String,
    val missingExceptionSimpleName: String,
    val throwingImplementationName: String,
)
