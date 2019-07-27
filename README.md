# Companion Vals

## TLDR

~~~~kotlin
data class Person(val firstName: String, val lastName: String) {
    fun sayMyName(shout: Boolean = false): String {
        return "$firstName $lastName".let { if (shout) it.capitalize() else it }
    }
}
data class Wrapper(@Companion val person: Person)

fun main() {
    val wrapper = Wrapper(Person("Max", "Mustermann"))
    println(wrapper.firstName) // prints Max
    println(wrapper.sayMyName()) // prints "Max Mustermann"
}

~~~~

## Details

There's a language feature proposal for Kotlin that talks about [companion values](https://github.com/Kotlin/KEEP/issues/114).
The proposal includes several aspects, most notably two:

* mark a val as companion and make it a receiver in corresponding scopes
* export members of companion vals of an instance, so that they can be used as if they were called on the companion val itself

I tried to implement these functionality as a compiler feature in the Kotlin compiler, but I wasn't good enough to get
the second aspect to work for simple scopes (like in the example above.),
but only for complex lexical scopes, like function calls, lambdas with receiver etc.
Take a look at [my other repository](https://github.com/hannespernpeintner/kotlin/tree/keep-106) for some examples.

And then I realized that it's not necessary to patch the compiler in order to make companion property members available
for the enclosing instance: One can use an annotation processor and generate extension members, much easier.
These delegates can be marked as inline, so that there's no runtime overhead compared to handwritten accessors at all.
