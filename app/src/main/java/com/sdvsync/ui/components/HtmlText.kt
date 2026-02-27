package com.sdvsync.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

val HTML_TAG_REGEX = Regex("<[a-zA-Z/]")
val BBCODE_TAG_REGEX = Regex(
    "\\[(?:b|i|u|s|url|img|size|color|font|list|quote|code|center|spoiler|line|heading)[=\\]/]",
    RegexOption.IGNORE_CASE
)

fun bbCodeToHtml(bbcode: String): String {
    var html = bbcode

    // Simple paired tags: [b]-><b>, [i]-><i>, etc.
    for (tag in listOf("b", "i", "u", "s")) {
        html = html.replace(Regex("\\[$tag]", RegexOption.IGNORE_CASE), "<$tag>")
        html = html.replace(Regex("\\[/$tag]", RegexOption.IGNORE_CASE), "</$tag>")
    }

    // [url=X]Y[/url] -> <a href="X">Y</a>
    html = html.replace(
        Regex("\\[url=([^\\]]+)](.*?)\\[/url]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    ) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[2]}</a>" }

    // [url]X[/url] -> <a href="X">X</a>
    html = html.replace(
        Regex("\\[url](.*?)\\[/url]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    ) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }

    // [img]X[/img] -> strip (can't render in TextView)
    html = html.replace(Regex("\\[img](.*?)\\[/img]", RegexOption.IGNORE_CASE), "")

    // [size=N] -> <big>
    html = html.replace(Regex("\\[size=[^\\]]+]", RegexOption.IGNORE_CASE), "<big>")
    html = html.replace(Regex("\\[/size]", RegexOption.IGNORE_CASE), "</big>")

    // [color=X] -> <font color="X">
    html = html.replace(Regex("\\[color=([^\\]]+)]", RegexOption.IGNORE_CASE)) {
        "<font color=\"${it.groupValues[1]}\">"
    }
    html = html.replace(Regex("\\[/color]", RegexOption.IGNORE_CASE), "</font>")

    // [font=X] -> strip (not supported in HtmlCompat)
    html = html.replace(Regex("\\[font=[^\\]]+]", RegexOption.IGNORE_CASE), "")
    html = html.replace(Regex("\\[/font]", RegexOption.IGNORE_CASE), "")

    // Lists: [list]->remove, [*]->bullet
    html = html.replace(Regex("\\[list(?:=[^\\]]*)?]", RegexOption.IGNORE_CASE), "")
    html = html.replace(Regex("\\[/list]", RegexOption.IGNORE_CASE), "")
    html = html.replace("[*]", "<br>&#8226; ")

    // [quote] -> blockquote
    html = html.replace(Regex("\\[quote(?:=[^\\]]*)?]", RegexOption.IGNORE_CASE), "<blockquote>")
    html = html.replace(Regex("\\[/quote]", RegexOption.IGNORE_CASE), "</blockquote>")

    // [code] -> monospace
    html = html.replace(Regex("\\[code]", RegexOption.IGNORE_CASE), "<tt>")
    html = html.replace(Regex("\\[/code]", RegexOption.IGNORE_CASE), "</tt>")

    // Strip unsupported tags
    for (tag in listOf("center", "spoiler", "heading")) {
        html = html.replace(Regex("\\[/?$tag]", RegexOption.IGNORE_CASE), "")
    }

    // [line] -> horizontal rule text
    html = html.replace(Regex("\\[line]", RegexOption.IGNORE_CASE), "<br>──────────<br>")

    // Newlines -> <br>
    html = html.replace("\r\n", "<br>").replace("\n", "<br>")

    return html
}

/**
 * Detects BBCode or HTML in the text and returns processed HTML string,
 * or null if the text is plain.
 */
fun toHtmlIfFormatted(text: String): String? = when {
    BBCODE_TAG_REGEX.containsMatchIn(text) -> bbCodeToHtml(text)
    HTML_TAG_REGEX.containsMatchIn(text) -> text
    else -> null
}

@Composable
fun HtmlText(html: String, textColor: Int, linkColor: Int, textSizeSp: Float, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = textSizeSp
            }
        },
        update = { tv ->
            tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}
