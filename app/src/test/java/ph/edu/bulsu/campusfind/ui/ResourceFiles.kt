package ph.edu.bulsu.campusfind.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Reads app resources from disk. Unit tests run with the app module as the working directory. */
object ResourceFiles {
    val res = File("src/main/res")
    val java = File("src/main/java")

    /** name to raw value ("#RRGGBB" or "@color/other") from a colors.xml. */
    fun colors(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/colors.xml"))
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.trim()
        }
    }

    /** All string and plural texts in values/strings.xml. */
    fun strings(): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "values/strings.xml"))
        val result = mutableListOf<Pair<String, String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            result += node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val node = items.item(i)
            val parentName = node.parentNode.attributes?.getNamedItem("name")?.nodeValue.orEmpty()
            result += parentName to node.textContent
        }
        return result
    }

    fun layouts(): List<File> = File(res, "layout").listFiles { f -> f.extension == "xml" }.orEmpty().toList()

    fun valuesXml(): List<File> = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        .flatMap { dir -> dir.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }
}
