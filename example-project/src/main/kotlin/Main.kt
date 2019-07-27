package main.persons

import de.hanno.companionvals.Companion

data class Person(val firstName: String, val lastName: String) {
    fun sayMyName(shout: Boolean = true): String {
        return "$firstName $lastName".let { if (shout) it.toUpperCase() else it }
    }
}
data class Address(val street: String)

data class Wrapper(@Companion val person: Person, @Companion val address: Address = Address("Fakestreet"))

data class WrapperWithInternalCompanion(@Companion internal val person: Person)

class WrapperWithCompanionInBody {
    @Companion val person: Person = Person("Max", "Mustermann")
}
