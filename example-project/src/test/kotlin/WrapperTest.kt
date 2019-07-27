import main.persons.Address
import main.persons.Person
import main.persons.Wrapper
import org.junit.Assert
import org.junit.Test

class WrapperTest {
    @Test
    fun `companion properties can be accessed`() {
        val wrapper = Wrapper(Person("Max", "Mustermann"), Address("Bakerstreet"))
        Assert.assertEquals("Max", wrapper.firstName)
        Assert.assertEquals("Mustermann", wrapper.lastName)
        Assert.assertEquals("Bakerstreet", wrapper.street)
    }

    @Test
    fun `companion functions can be accessed`() {
        val wrapper = Wrapper(Person("Max", "Mustermann"))
        Assert.assertEquals("Max Mustermann", wrapper.sayMyName())
    }

    @Test
    fun `companion functions with parameters are forwarded correctly`() {
        val wrapper = Wrapper(Person("Max", "Mustermann"))
        Assert.assertEquals("Max Mustermann", wrapper.sayMyName(shout = true))
    }

}