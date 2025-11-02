import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.inventory.data.InventoryDatabase
import com.example.inventory.data.ItemDAO
import org.junit.Before
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.inventory.data.Item
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import java.io.IOException
import org.junit.Test
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ItemDaoTest {

    private lateinit var itemDao: ItemDAO
    private lateinit var inventoryDatabase: InventoryDatabase

    private var item1 = Item(1, "Apples", 10.0, 20,"supplier1","supplier1@mail.ru","+71234567890")
    private var item2 = Item(2, "Bananas", 15.0, 97,"supplier2","supplier2@mail.ru","+73216548709")


    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        inventoryDatabase = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java)
            // Allowing main thread queries, just for testing.
            .allowMainThreadQueries()
            .build()
        itemDao = inventoryDatabase.getItemDAO()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        inventoryDatabase.close()
    }


    private suspend fun addOneItemToDb() {
        itemDao.insert(item1)
    }

    private suspend fun addTwoItemsToDb() {
        itemDao.insert(item1)
        itemDao.insert(item2)
    }

    @Test
    @Throws(Exception::class)
    fun daoInsert_insertsItemIntoDB() = runBlocking {
        addOneItemToDb()
        val allItems = itemDao.findAll().first()
        assertEquals(allItems[0], item1)
    }

    @Test
    @Throws(Exception::class)
    fun daoGetAllItems_returnsAllItemsFromDB() = runBlocking {
        addTwoItemsToDb()
        val allItems = itemDao.findAll().first()
        assertEquals(allItems[0], item1)
        assertEquals(allItems[1], item2)
    }

    @Test
    @Throws(Exception::class)
    fun daoUpdateItems_updatesItemsInDB() = runBlocking {
        addTwoItemsToDb()
        itemDao.update(item1.copy(price = 20.0, quantity = 10))
        itemDao.update(item2.copy(price = 5.0, quantity = 50))

        val allItems = itemDao.findAll().first()
        assertEquals(allItems[0], Item(1, "Apples", 20.0, 10,"supplier1","supplier1@mail.ru","+71234567890"))
        assertEquals(allItems[1], Item(2, "Bananas", 5.0, 50,"supplier2","supplier2@mail.ru","+73216548709"))
    }

    @Test
    @Throws(Exception::class)
    fun daoDeleteItems_deletesAllItemsFromDB() = runBlocking {
        addTwoItemsToDb()
        itemDao.delete(item1)
        itemDao.delete(item2)
        val allItems = itemDao.findAll().first()
        assertTrue(allItems.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun daoGetItem_returnsItemFromDB() = runBlocking {
        addOneItemToDb()
        val item = itemDao.findById(1)
        assertEquals(item.first(), item1)
    }
}