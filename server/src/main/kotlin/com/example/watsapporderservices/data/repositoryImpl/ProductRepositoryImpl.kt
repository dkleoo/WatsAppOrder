package com.example.watsapporderservices.data.repositoryImpl

import com.example.watsapporderservices.data.database.input.InputDao
import com.example.watsapporderservices.data.database.input.InputEntity
import com.example.watsapporderservices.data.database.product.ProductDao
import com.example.watsapporderservices.data.database.product.ProductEntity
import com.example.watsapporderservices.data.database.step.StepDao
import com.example.watsapporderservices.data.database.step.StepEntity
import com.example.watsapporderservices.data.database.step.StepInputDao
import com.example.watsapporderservices.data.database.store.StoreDao
import com.example.watsapporderservices.data.database.store.StoreEntity
import com.example.watsapporderservices.data.enum.ProductType
import com.example.watsapporderservices.data.mapper.ProductRequest
import com.example.watsapporderservices.data.mapper.ProductResponse
import com.example.watsapporderservices.data.mapper.StepRequest
import com.example.watsapporderservices.data.mapper.toResponse
import com.example.watsapporderservices.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.text.Normalizer

private const val MIN_TOKEN_LENGTH = 2

private val DIACRITICS_REGEX = Regex("\\p{M}+")
private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")

private val SEARCH_STOPWORDS = setOf(
    "una", "uno", "un", "unos", "unas", "el", "la", "los", "las", "de", "del", "al", "a", "con", "sin",
    "para", "por", "y", "o", "en", "que", "me", "mi", "quiero", "quiere", "quisiera", "dame", "deme",
    "favor", "porfa", "porfavor", "gracias", "tienen", "tiene", "hay", "cual", "producto", "productos",
    "ordenar", "pedir", "seria", "es", "son", "the", "of", "and",
)

class ProductRepositoryImpl(
    private val productDao: ProductDao,
    private val stepDao: StepDao,
    private val stepInputDao: StepInputDao,
    private val inputDao: InputDao,
    private val storeDao: StoreDao,
) : ProductRepository {
    override suspend fun getProducts(): List<ProductResponse> = withContext(Dispatchers.IO) {
        transaction {
            val products = productDao.findAll()
            val stepsByProduct = stepDao.findByProductIds(products.map { it.id }).groupBy { it.productId }
            val inputsByStep = resolveInputsByStep(stepsByProduct.values.flatten().map { it.id })
            products.map { it.toResponse(stepsByProduct[it.id].orEmpty(), inputsByStep) }
        }
    }

    override suspend fun getProduct(id: Int): ProductResponse? = withContext(Dispatchers.IO) {
        transaction {
            val product = productDao.findById(id) ?: return@transaction null
            val steps = stepDao.findByProductId(id)
            product.toResponse(steps, resolveInputsByStep(steps.map { it.id }))
        }
    }

    override suspend fun searchByStore(
        whatsappBusinessPhone: String?,
        idWhatsApp: String?,
        query: String?,
    ): List<ProductResponse> = withContext(Dispatchers.IO) {
        transaction {
            val store = findStore(whatsappBusinessPhone, idWhatsApp)
            // If there is no store, or the store has no linked products, fall back to the full catalog.
            val candidates = store
                ?.let { productDao.findByStoreId(it.id) }
                ?.takeIf { it.isNotEmpty() }
                ?: productDao.findAll()
            val products = matchProducts(candidates, query)
            val stepsByProduct = stepDao.findByProductIds(products.map { it.id }).groupBy { it.productId }
            val inputsByStep = resolveInputsByStep(stepsByProduct.values.flatten().map { it.id })
            products.map { it.toResponse(stepsByProduct[it.id].orEmpty(), inputsByStep) }
        }
    }

    /**
     * Lenient product search: splits the query into significant words, normalizes them (lowercase,
     * without accents or punctuation) and keeps products whose name matches any word, ranking the ones
     * with more matches first. A blank query returns the whole menu.
     */
    private fun matchProducts(products: List<ProductEntity>, query: String?): List<ProductEntity> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return products
        return products
            .map { product -> product to score(product.name, tokens) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<ProductEntity, Int>> { it.second }.thenBy { it.first.name },
            )
            .map { it.first }
    }

    private fun score(name: String, tokens: List<String>): Int {
        val words = normalize(name).split(" ").filter { it.isNotBlank() }
        val compact = words.joinToString("")
        return tokens.count { token -> words.any { it.startsWith(token) } || compact.startsWith(token) }
    }

    private fun tokenize(query: String?): List<String> =
        query?.let {
            normalize(it).split(" ")
                .filter { word -> word.length >= MIN_TOKEN_LENGTH && word !in SEARCH_STOPWORDS }
        }.orEmpty()

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()

    override suspend fun saveProduct(request: ProductRequest): ProductResponse? = withContext(Dispatchers.IO) {
        transaction {
            val price = BigDecimal.valueOf(request.price)
            val cost = BigDecimal.valueOf(request.cost)
            val product = if (request.id == null) {
                productDao.insert(request.name, price, cost, request.quantity, request.type)
            } else {
                val updated = productDao.update(request.id, request.name, price, cost, request.quantity, request.type)
                if (updated == 0) return@transaction null
                ProductEntity(request.id, request.name, price, cost, request.quantity, request.type)
            }
            val saved = replaceSteps(product.id, request.type, request.steps)
            product.toResponse(saved.steps, saved.inputsByStep)
        }
    }

    override suspend fun updateProduct(id: Int, request: ProductRequest): ProductResponse? = withContext(Dispatchers.IO) {
        transaction {
            val price = BigDecimal.valueOf(request.price)
            val cost = BigDecimal.valueOf(request.cost)
            val updated = productDao.update(id, request.name, price, cost, request.quantity, request.type)
            if (updated == 0) return@transaction null
            val product = ProductEntity(id, request.name, price, cost, request.quantity, request.type)
            val saved = replaceSteps(id, request.type, request.steps)
            product.toResponse(saved.steps, saved.inputsByStep)
        }
    }

    override suspend fun deleteProduct(id: Int): Boolean = withContext(Dispatchers.IO) {
        transaction {
            if (productDao.findById(id) == null) return@transaction false
            val stepIds = stepDao.findByProductId(id).map { it.id }
            stepInputDao.deleteByStepIds(stepIds)
            stepDao.deleteByProductId(id)
            productDao.deleteById(id)
            true
        }
    }

    private fun findStore(whatsappBusinessPhone: String?, idWhatsApp: String?): StoreEntity? {
        val phoneDigits = whatsappBusinessPhone?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
        if (phoneDigits != null) storeDao.findByWhatsappBusinessPhone(phoneDigits)?.let { return it }
        return idWhatsApp?.takeIf { it.isNotBlank() }?.let { storeDao.findByIdWhatsApp(it) }
    }

    private fun resolveInputsByStep(stepIds: List<Int>): Map<Int, List<InputEntity>> {        val links = stepInputDao.findByStepIds(stepIds)
        val inputsById = inputDao.findByIds(links.map { it.inputId }.distinct()).associateBy { it.id }
        return links.groupBy { it.stepId }
            .mapValues { (_, stepLinks) -> stepLinks.mapNotNull { inputsById[it.inputId] } }
    }

    private fun replaceSteps(productId: Int, type: ProductType, steps: List<StepRequest>): SavedSteps {
        val existingStepIds = stepDao.findByProductId(productId).map { it.id }
        stepInputDao.deleteByStepIds(existingStepIds)
        stepDao.deleteByProductId(productId)
        if (type != ProductType.WITH_INPUTS) {
            return SavedSteps(emptyList(), emptyMap())
        }
        val savedSteps = mutableListOf<StepEntity>()
        val inputsByStep = mutableMapOf<Int, List<InputEntity>>()
        steps.forEach { stepRequest ->
            val step = stepDao.insert(productId, stepRequest.name, stepRequest.position)
            val inputs = inputDao.findByIds(stepRequest.inputIds.distinct())
            inputs.forEach { stepInputDao.insert(step.id, it.id) }
            savedSteps += step
            inputsByStep[step.id] = inputs
        }
        return SavedSteps(savedSteps, inputsByStep)
    }

    private data class SavedSteps(
        val steps: List<StepEntity>,
        val inputsByStep: Map<Int, List<InputEntity>>,
    )
}
