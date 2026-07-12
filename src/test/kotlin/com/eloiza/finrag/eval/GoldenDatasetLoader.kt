package com.eloiza.finrag.eval

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File

/**
 * Carrega e valida o golden dataset e o corpus de avaliação do classpath de
 * teste. O mapeamento do JSON é explícito (campo a campo, sem binding
 * automático) para que qualquer problema no dataset falhe com mensagem clara
 * apontando o caso ofensor — o dataset é editado à mão e vai evoluir.
 */
object GoldenDatasetLoader {
    private const val DATASET_RESOURCE = "/golden/golden-dataset.json"
    private const val CORPUS_DIR_RESOURCE = "/golden/corpus"

    private val objectMapper = ObjectMapper()

    /** Conteúdo bruto de cada documento do corpus, indexado pelo nome do arquivo. */
    fun loadCorpus(): Map<String, String> {
        val dirUrl =
            requireNotNull(javaClass.getResource(CORPUS_DIR_RESOURCE)) {
                "diretório de corpus não encontrado no classpath: $CORPUS_DIR_RESOURCE"
            }
        val files = File(dirUrl.toURI()).listFiles { file -> file.extension == "md" }.orEmpty()
        require(files.isNotEmpty()) { "nenhum documento .md encontrado em $CORPUS_DIR_RESOURCE" }
        return files.associate { it.name to it.readText() }
    }

    /** Carrega os casos já validados contra o corpus. */
    fun loadCases(): List<GoldenCase> {
        val json =
            requireNotNull(javaClass.getResourceAsStream(DATASET_RESOURCE)) {
                "dataset não encontrado no classpath: $DATASET_RESOURCE"
            }.use { objectMapper.readTree(it) }

        val cases = json.path("cases")
        require(cases.isArray && !cases.isEmpty) { "dataset sem lista 'cases'" }

        val parsed = (0 until cases.size()).map { parseCase(cases.get(it)) }
        validate(parsed, loadCorpus())
        return parsed
    }

    private fun parseCase(node: JsonNode): GoldenCase {
        val id = node.requiredText("id", context = node.path("id").asString(""))
        return GoldenCase(
            id = id,
            question = node.requiredText("question", id),
            hasAnswer =
                node.path("hasAnswer").let {
                    require(it.isBoolean) { "caso '$id': campo 'hasAnswer' ausente ou não booleano" }
                    it.booleanValue()
                },
            expectedDocument = node.path("expectedDocument").takeIf { it.isString }?.stringValue(),
            expectedSubstrings =
                node.path("expectedSubstrings").let { array ->
                    (0 until array.size()).map { array.get(it).stringValue() }
                },
            referenceAnswer = node.requiredText("referenceAnswer", id),
            notes = node.path("notes").takeIf { it.isString }?.stringValue(),
        )
    }

    private fun JsonNode.requiredText(
        field: String,
        context: String,
    ): String {
        val value = path(field)
        require(value.isString && value.stringValue().isNotBlank()) {
            "caso '$context': campo '$field' ausente ou vazio"
        }
        return value.stringValue()
    }

    private fun validate(
        cases: List<GoldenCase>,
        corpus: Map<String, String>,
    ) {
        val duplicated = cases.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "ids duplicados no dataset: $duplicated" }

        val normalizedCorpus = corpus.mapValues { (_, text) -> normalizeWhitespace(text) }
        for (case in cases) {
            if (!case.hasAnswer) {
                require(case.expectedDocument == null && case.expectedSubstrings.isEmpty()) {
                    "caso '${case.id}': hasAnswer=false não deve ter expectedDocument/expectedSubstrings"
                }
                continue
            }

            val document =
                requireNotNull(case.expectedDocument) {
                    "caso '${case.id}': hasAnswer=true exige expectedDocument"
                }
            val documentText =
                requireNotNull(normalizedCorpus[document]) {
                    "caso '${case.id}': expectedDocument '$document' não existe no corpus ${corpus.keys}"
                }
            require(case.expectedSubstrings.isNotEmpty()) {
                "caso '${case.id}': hasAnswer=true exige ao menos uma expectedSubstring"
            }
            for (substring in case.expectedSubstrings) {
                val normalized = normalizeWhitespace(substring)
                require(documentText.contains(normalized)) {
                    "caso '${case.id}': substring \"$substring\" não encontrada em $document"
                }
                val elsewhere = normalizedCorpus.filter { (name, text) -> name != document && text.contains(normalized) }.keys
                require(elsewhere.isEmpty()) {
                    "caso '${case.id}': substring \"$substring\" não é única — também aparece em $elsewhere"
                }
            }
        }
    }
}
