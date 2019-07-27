import main.persons.*
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
        Assert.assertEquals("MAX MUSTERMANN", wrapper.sayMyName())
    }

    @Test
    fun `companion functions with parameters are forwarded correctly`() {
        val wrapper = Wrapper(Person("Max", "Mustermann"))
        Assert.assertEquals("MAX MUSTERMANN", wrapper.sayMyName(shout = true))
    }

    @Test
    fun `companion extension functions are forwarded correctly`() {
        val wrapper = WrapperWithCompanionInBody()
        Assert.assertEquals("MAX MUSTERMANN", wrapper.sayMyName(shout = true))
    }

}