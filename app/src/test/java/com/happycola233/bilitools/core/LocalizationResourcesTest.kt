package com.happycola233.bilitools.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** 从源文件校验，防止 Android 的默认资源回退掩盖漏译和占位符损坏。 */
class LocalizationResourcesTest {
    private val resources = File("src/main/res")
    private val qualifiers = listOf(
        "b+zh+Hans", "b+zh+Hant", "en", "ja", "es", "pt", "ar",
        "ru", "tr", "th", "ms", "vi", "in",
    )
    private val placeholder = Regex("%(?:[0-9]+\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z%]")
    private val templateToken = Regex("\\{[a-z][a-z0-9_]*(?::[^{}]+)?}")

    @Test
    fun everySupportedLanguageHasEveryTranslatableResourceAndNoExtraKeys() {
        val source = readStrings(File(resources, "values"))
        val expected = source.filterValues { it.translatable }.keys
        qualifiers.forEach { qualifier ->
            val translated = readStrings(File(resources, "values-$qualifier"))
            assertEquals("Resource coverage: $qualifier", expected, translated.keys)
            translated.forEach { (name, resource) ->
                assertTrue("Empty translation: $qualifier/$name", resource.text.isNotBlank())
                assertFalse("Unresolved translation marker: $qualifier/$name", resource.text.contains("__VAR_"))
                assertFalse("Invalid Unicode: $qualifier/$name", resource.text.contains('\uFFFD'))
            }
        }
    }

    @Test
    fun translatedFormattingArgumentsAndNamingTokensPreserveTheirMeaning() {
        val source = readStrings(File(resources, "values"))
        qualifiers.forEach { qualifier ->
            readStrings(File(resources, "values-$qualifier")).forEach { (name, translated) ->
                val original = requireNotNull(source[name]) { "Unknown resource $qualifier/$name" }
                assertEquals(
                    "Formatting arguments: $qualifier/$name",
                    placeholder.findAll(original.text).map { it.value }.sorted().toList(),
                    placeholder.findAll(translated.text).map { it.value }.sorted().toList(),
                )
                assertEquals(
                    "Naming template syntax: $qualifier/$name",
                    templateToken.findAll(original.text).map { it.value }.sorted().toList(),
                    templateToken.findAll(translated.text).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test
    fun simplifiedChineseIsExplicitSoItParticipatesInSystemLanguageMatching() {
        val source = readStrings(File(resources, "values")).filterValues { it.translatable }
        assertEquals(source, readStrings(File(resources, "values-b+zh+Hans")))
    }

    @Test
    fun quantityResourcesCoverEveryLanguageWithValidFormattingArguments() {
        fun readPlurals(directory: File): Map<String, Map<String, String>> {
            val result = linkedMapOf<String, Map<String, String>>()
            directory.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }
                .orEmpty().forEach { file ->
                    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("plurals")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index) as Element
                        val name = node.getAttribute("name")
                        assertFalse("Duplicate quantity resource: $directory/$name", result.containsKey(name))
                        val items = node.getElementsByTagName("item")
                        val forms = linkedMapOf<String, String>()
                        for (itemIndex in 0 until items.length) {
                            val item = items.item(itemIndex) as Element
                            val quantity = item.getAttribute("quantity")
                            assertFalse("Duplicate quantity: $directory/$name/$quantity", forms.containsKey(quantity))
                            forms[quantity] = item.textContent
                        }
                        result[name] = forms
                    }
                }
            return result
        }
        val source = readPlurals(File(resources, "values"))
        qualifiers.forEach { qualifier ->
            val translated = readPlurals(File(resources, "values-$qualifier"))
            assertEquals("Quantity coverage: $qualifier", source.keys, translated.keys)
            translated.forEach { (name, forms) ->
                assertTrue("Missing other form: $qualifier/$name", forms.containsKey("other"))
                val expected = placeholder.findAll(source.getValue(name).getValue("other")).map { it.value }.toSet()
                forms.forEach { (quantity, text) ->
                    assertTrue("Empty quantity: $qualifier/$name/$quantity", text.isNotBlank())
                    val actual = placeholder.findAll(text).map { it.value }.toSet()
                    // 阿拉伯语单数、双数可以由名词自身表达数量，其余形式必须保留数字。
                    if (qualifier == "ar" && quantity in setOf("zero", "one", "two")) {
                        assertTrue("Invalid quantity arguments: $qualifier/$name/$quantity", expected.containsAll(actual))
                    } else assertEquals("Quantity arguments: $qualifier/$name/$quantity", expected, actual)
                }
            }
        }
        assertEquals(source, readPlurals(File(resources, "values-b+zh+Hans")))
    }

    private fun readStrings(directory: File): Map<String, TextResource> {
        assertTrue("Missing locale directory: $directory", directory.isDirectory)
        val result = linkedMapOf<String, TextResource>()
        directory.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }
            .orEmpty().sortedBy(File::getName).forEach { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = document.documentElement.childNodes
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index) as? Element ?: continue
                    if (node.tagName != "string") continue
                    val name = node.getAttribute("name")
                    assertFalse("Duplicate resource $directory/$name", result.containsKey(name))
                    result[name] = TextResource(node.textContent, node.getAttribute("translatable") != "false")
                }
            }
        return result
    }

    private data class TextResource(val text: String, val translatable: Boolean)
}
