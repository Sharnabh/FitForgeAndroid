package com.fitforge

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class ListItem(val number: String?, val text: String, val indentLevel: Int) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Parses raw markdown lines into structured block elements.
 */
fun parseMarkdownBlocks(rawMarkdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawMarkdown.lines()
    var inCodeBlock = false
    var codeLang: String? = null
    val codeBuilder = StringBuilder()
    val currentParagraph = mutableListOf<String>()

    fun flushParagraph() {
        if (currentParagraph.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(currentParagraph.joinToString("\n")))
            currentParagraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // Code block toggle (```)
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                codeLang = null
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = line.trimStart().removePrefix("```").trim().ifEmpty { null }
            }
            i++
            continue
        }

        if (inCodeBlock) {
            if (codeBuilder.isNotEmpty()) codeBuilder.append("\n")
            codeBuilder.append(line)
            i++
            continue
        }

        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // Horizontal divider (---, ***, ___)
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Headings (#, ##, ###, etc.)
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                flushParagraph()
                val headerText = trimmed.substring(level + 1).trim()
                blocks.add(MarkdownBlock.Header(level, headerText))
                i++
                continue
            }
        }

        // Blockquotes (> ...)
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteText = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.BlockQuote(quoteText))
            i++
            continue
        }

        // Unordered lists (- ..., * ..., + ...)
        val bulletMatch = Regex("""^(\s*)([-*+])\s+(.*)$""").matchEntire(line)
        if (bulletMatch != null) {
            flushParagraph()
            val indent = (bulletMatch.groupValues[1].length / 2).coerceAtMost(3)
            val text = bulletMatch.groupValues[3]
            blocks.add(MarkdownBlock.ListItem(number = null, text = text, indentLevel = indent))
            i++
            continue
        }

        // Ordered lists (1. ..., 2. ...)
        val numMatch = Regex("""^(\s*)(\d+)\.\s+(.*)$""").matchEntire(line)
        if (numMatch != null) {
            flushParagraph()
            val indent = (numMatch.groupValues[1].length / 2).coerceAtMost(3)
            val num = numMatch.groupValues[2]
            val text = numMatch.groupValues[3]
            blocks.add(MarkdownBlock.ListItem(number = "$num.", text = text, indentLevel = indent))
            i++
            continue
        }

        // Regular paragraph text line
        currentParagraph.add(line)
        i++
    }

    flushParagraph()

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
    }

    return blocks
}

/**
 * Parses inline markdown tokens (bold, italic, strikethrough, inline code, links)
 * into an AnnotatedString.
 */
fun parseMarkdownToAnnotatedString(
    text: String,
    baseColor: Color,
    linkColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val pattern = Pattern.compile(
        """(\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|\*(.+?)\*|~~(.+?)~~|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))"""
    )
    val matcher = pattern.matcher(text)
    var lastIndex = 0

    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()

        if (start > lastIndex) {
            builder.append(text.substring(lastIndex, start))
        }

        val fullMatch = matcher.group(0) ?: ""
        when {
            // Bold Italic: ***text***
            fullMatch.startsWith("***") && fullMatch.endsWith("***") && fullMatch.length >= 6 -> {
                val inner = fullMatch.substring(3, fullMatch.length - 3)
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                builder.append(inner)
                builder.pop()
            }
            // Bold: **text**
            fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4 -> {
                val inner = fullMatch.substring(2, fullMatch.length - 2)
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(inner)
                builder.pop()
            }
            // Inline code: `text`
            fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                val inner = fullMatch.substring(1, fullMatch.length - 1)
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                        color = codeTextColor,
                        fontSize = 13.sp
                    )
                )
                builder.append(" $inner ")
                builder.pop()
            }
            // Strikethrough: ~~text~~
            fullMatch.startsWith("~~") && fullMatch.endsWith("~~") && fullMatch.length >= 4 -> {
                val inner = fullMatch.substring(2, fullMatch.length - 2)
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                builder.append(inner)
                builder.pop()
            }
            // Italic: *text*
            fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                val inner = fullMatch.substring(1, fullMatch.length - 1)
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(inner)
                builder.pop()
            }
            // Link: [label](url)
            fullMatch.startsWith("[") && fullMatch.contains("](") -> {
                val label = fullMatch.substringAfter("[").substringBefore("](")
                builder.pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                builder.append(label)
                builder.pop()
            }
            else -> {
                builder.append(fullMatch)
            }
        }
        lastIndex = end
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

/**
 * Rich Jetpack Compose Markdown renderer supporting headings, bullet lists,
 * numbered lists, blockquotes, code blocks, horizontal dividers, and inline formatting.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    if (markdown.isBlank()) return

    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val codeTextColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = remember(block.text, color) {
                            parseMarkdownToAnnotatedString(block.text, color, linkColor, codeBgColor, codeTextColor)
                        },
                        style = headerStyle,
                        color = color
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = remember(block.text, color) {
                            parseMarkdownToAnnotatedString(block.text, color, linkColor, codeBgColor, codeTextColor)
                        },
                        style = style,
                        color = color
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val bullet = block.number ?: "•"
                        Text(
                            text = bullet,
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = if (block.number != null) color else linkColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = remember(block.text, color) {
                                parseMarkdownToAnnotatedString(block.text, color, linkColor, codeBgColor, codeTextColor)
                            },
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(linkColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = remember(block.text, color) {
                                parseMarkdownToAnnotatedString(block.text, color, linkColor, codeBgColor, codeTextColor)
                            },
                            style = style.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            if (!block.language.isNullOrBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = block.code,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = color
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

