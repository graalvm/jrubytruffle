# Truffle Interop

TruffleRuby supports standard Truffle API interop messages. This document
explains what it does when it receives them, how to get it to explicitly send
them, how to get it to send them using more idiomatic Ruby, and how what
messages it sends for normal Ruby operations on foreign objects.

This document only explains how TruffleRuby uses messages. The messages
themselves are explained in the
[Truffle JavaDoc](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/Message.html).

There is a separate document aimed at people using interop to write
[polyglot programs](../user/polyglot.md). This document gives more internal
details.

* [How Ruby responds to messages](#how-ruby-responds-to-messages)
* [How to explicitly send messages from Ruby](#how-to-explicitly-send-messages-from-ruby)
* [How to send messages using idiomatic Ruby](#how-to-send-messages-using-idiomatic-ruby)
* [What messages are sent for Ruby syntax on foreign objects](#what-messages-are-sent-for-ruby-syntax-on-foreign-objects)
* [String conversion](#string-conversion)
* [Import and export](#import-and-export)
* [Interop eval](#interop-eval)
* [Java interop](#java-interop)
* [Additional methods](#additional-methods)
* [Notes on method resolution](#notes-on-method-resolution)

Also see the separate document on
[JRuby-compatible Java interop](../user/jruby-java-interop.md).

## How Ruby responds to messages

### `IS_EXECUTABLE`

Returns true only for instances of `Method` and `Proc`.

### `EXECUTE`

Calls either a `Method` or `Proc`, passing the arguments as you'd expect.
Doesn't pass a block.

### `INVOKE`

Calls the method with the name you provided, passing the arguments as you'd
expect. Doesn't pass a block.

### `IS_INSTANTIABLE`

Returns if the object responds to `new`.

### `NEW`

Calls `new`, passing the arguments as you'd expect.

### `HAS_SIZE`

Returns true if the object responds to `size`.

### `GET_SIZE`

Call `size` on the object.

### `IS_BOXED`

Returns true only for instances of `String`, `Symbol`,
`Truffle::FFI::Pointer` and objects that respond to `unbox`.

### `UNBOX`

For a `String` or `Symbol`, produces a `java.lang.String`, similar to
`Truffle::Interop.to_java_string`. For a `Truffle::FFI::Pointer`,
produces the address as a `long`. For all other objects calls `unbox`.

### `IS_POINTER`

Calls `pointer?` if the object responds to it, otherwise returns `false`.

### `AS_POINTER`

Calls `address` if the object responds to it, otherwise throws
`UnsupportedMessageException`.

### `TO_NATIVE`

Calls `to_native` if the object responds to it, otherwise throws
`UnsupportedMessageException`.

### `IS_NULL`

Returns true only for the `nil` object.

### `HAS_KEYS`

Returns true.

### `KEYS`

If the receiver is a Ruby `Hash`, return the hash keys converted to
strings.

Otherwise, return all method names via `receiver.methods`, and instance
variable names if the `internal` flag is set.

Keys are returned as a Ruby `Array` containing Java `String` objects or
integers.

### `KEY_INFO`

If the receiver is a Ruby `Array`:

- `READABLE` will set if the name is an integer and in bounds.

- `INSERTABLE` and `MODIFIABLE` will be set if the index is an integer, in
  bounds and the array is not frozen.

- `REMOVABLE`, `INVOCABLE` and `INTERNAL` will not be set.

Otherwise, if the receiver is a Ruby `Hash`:

- `READABLE` will be set if the key is found.

- `INSERTABLE` will be set if the hash is not frozen.

- `MODIFIABLE` and `REMOVABLE`  will be set if the key is found and the hash is
  not frozen.

- `INVOCABLE` and `INTERNAL` will not be set.

Otherwise if the name starts with an `@`:

- `READABLE` will be set if the instance variable exists.

- `INSERTABLE` will be set if the object is not frozen.

- `MODIFIABLE` and `REMOVABLE` will be set if the instance variable exists and
  the object is not frozen.

- `INVOCABLE` will not be set.

- `INTERNAL` will be set.

Otherwise:

- `READABLE` will be set if the object has a method of that name, or the object
  has a `[]` method.

- `INSERTABLE` and `MODIFIABLE` will be set if the object has a `[]=` method.

- `REMOVABLE` and `INTERNAL` will not be set.

- `INVOCABLE` will be set if the object has a method of that name.

For all objects:

- `EXISTING` is set if `READABLE`, `MODIFIABLE`, `INVOCABLE`, `INTERNAL` or
  `REMOVABLE` are set.

### `READ`

If the receiver is a Ruby `Array` or `Hash`, call `receiver[name/index]`.

Otherwise if the name starts with an `@` it is read as an instance variable.

Otherwise, if there is a method `[]` defined on the receiver, call
`receiver[name/index]`.

Otherwise, if there is a method defined on the object with the same name, return
it as a (bound) `Method`.

Otherwise, throw `UnknownIdentifierException`.

### `WRITE`

If the receiver is a Ruby `Array` or `Hash`, call
`receiver[name/index] = value`.

Otherwise if the name starts with an `@` it is set as an instance variable.

Otherwise, if there is a method called `[]=` on the receiver, call
`receiver[name/index] = value`.

Otherwise, throw `UnknownIdentifierException`.

### `REMOVE`

If the receiver is a Ruby `Array` and the name is an integer, delete the element
at the index indicated by the name value. If the index is out of bounds then, in
keeping with Ruby semantics, no-op. If the name value is not an integer, then
`UnknownIdentifierException` is thrown.

Otherwise, if the receiver is a Ruby `Hash`, delete the key indicated by the
name value. If no such key exists then, in keeping with Ruby semantics, no-op.

Otherwise, if the name starts with an `@` remove it as an instance variable.

Otherwise, throw `UnknownIdentifierException`.

## How to explicitly send messages from Ruby

### `IS_EXECUTABLE`

`Truffle::Interop.executable?(value)`

### `EXECUTE`

`Truffle::Interop.execute(receiver, *args)`

`Truffle::Interop.execute_without_conversion(receiver, *args)`

### `INVOKE`

`Truffle::Interop.invoke(receiver, name, *args)`

`name` can be a `String` or `Symbol`.

### `IS_INSTANTIABLE`

`Truffle::Interop.instantiable?(receiver)`

### `NEW`

`Truffle::Interop.new(receiver, *args)`

### `HAS_SIZE`

`Truffle::Interop.size?(value)`

### `GET_SIZE`

`Truffle::Interop.size(value)`

### `IS_BOXED`

`Truffle::Interop.boxed?(value)`

### `UNBOX`

`Truffle::Interop.unbox(value)`

`Truffle::Interop.unbox_without_conversion(value)`

### `IS_POINTER`

`Truffle::Interop.pointer?(value)`

### `AS_POINTER`

`Truffle::Interop.as_pointer(value)`

### `TO_NATIVE`

`Truffle::Interop.to_native(value)`

### `IS_NULL`

`Truffle::Interop.null?(value)`

### `HAS_KEYS`

`Truffle::Interop.keys?(value)`

### `KEYS`

`Truffle::Interop.keys(value, internal=false)`

TruffleRuby will convert the returned value from a foreign object of Java
`String` objects, to a Ruby `Array` of Ruby `String` objects.

`Truffle::Interop.keys_without_conversion(value, internal=false)`

### `KEY_INFO`

`Truffle::Interop.key_info(object, name)`

Returns an array containing zero or more of the symbols
`[:existing, :readable, :writable, :invocable, :internal, :removable, :modifiable, :insertable]`
in an undefined order.

### `READ`

`Truffle::Interop.read(object, name/index)`

### `WRITE`

`Truffle::Interop.write(object, name/index, value)`

### `REMOVE`

`Truffle::Interop.remove(object, name/index)`

## How to send messages using idiomatic Ruby

### `IS_EXECUTABLE`

`object.respond_to?(:call)`

### `EXECUTE`

`object.call(*args)`

### `INVOKE`

`object.name`

`object.name(*args)`

### `IS_INSTANTIABLE`

`object.respond_to?(:new)`

### `NEW`

`object.new(*args)`

### `HAS_SIZE`

`object.respond_to?(:size)`

### `GET_SIZE`

`value.size`

### `IS_BOXED`

Not supported.

### `UNBOX`

Not supported.

### `IS_POINTER`

Not supported.

### `AS_POINTER`

Not supported.

### `TO_NATIVE`

Not supported.

### `IS_NULL`

`value.nil?`

### `HAS_KEYS`

`object.respond_to?(:keys)`

### `KEYS`

`value.keys`

### `READ`

`object[name/index]`, where name is a `String` or `Symbol` in most cases, or an
integer, or anything else

### `WRITE`

`object[name/index] = value`, where name is a `String` or `Symbol` in most
cases, or an integer, or anything else

### `REMOVE`

Not supported.

## What messages are sent for Ruby syntax on foreign objects

TruffleRuby automatically provides these special methods on a foreign object.
They have priority over methods that the foreign object actually provides.

`object[name/index]` (`#[](name/index)`) sends `READ`.

`object[name/index] = value` (`#[]=(name/index, value)`) sends `WRITE`.

`object.delete(name/index)` sends `REMOVE`.

`object.call(*args)` sends `EXECUTE`.

`object.nil?` sends `IS_NIL`.

`object.size` sends `SIZE`.

`object.keys` sends `KEYS`.

`object.name` sends `INVOKE`.

`object + other` for will send `IS_BOXED` on `object` and based on that will
possibly `UNBOX` it, convert to Ruby, and then call the method on the unboxed
version.

`object.name(*args)` sends `INVOKE`.

`object.new(*args)` sends `NEW`.

`object.to_a` and `object.to_ary` calls `Truffle::Interop.to_array(object)`.

`object.equal?(other)` returns whether `object` is the same as `other` using
reference equality, like `BasicObject#equal?`. For Java interop objects it
looks at the underlying Java object.

`object.inspect` produces a simple string of the format
`#<Truffle::Interop::Foreign:system-identity-hash-code>`

`object.respond_to?(:to_a)`, `respond_to?(:to_ary)` and `respond_to?(:size)`
sends `HAS_SIZE`.

`object.respond_to?(:call)` sends `EXECUTABLE`.

`object.respond_to?(:new)` sends `IS_INSTANTIABLE`.

`object.respond_to?(name)` for other names returns `false`.

`object.__send__(name, *args)` works in the same way as literal method call on
the foreign object, including allowing the special-forms listed above (see
[notes on method resolution](#notes-on-method-resolution)).

## String conversion

Ruby strings and symbols are unboxable to Java strings.

A call from Ruby to a foreign language using `NEW`, `EXECUTE`, `INVOKE`, `READ`,
`WRITE`, or `UNBOX`, that has Ruby strings or symbols as arguments, will convert
each Ruby string or symbol argument to a Java string. You can avoid this
conversion for `EXECUTE` using `Truffle::Interop.execute_without_conversion`,
and for `UNBOX` using `Truffle::Interop.unbox_without_conversion`.

`Truffle::Interop.keys` converts Java string key names to Ruby strings, so it
also has a `Truffle::Interop.keys_without_conversion` equivalent.

A call from Ruby to a foreign language using `NEW`, `EXECUTE`, `INVOKE`, `READ`,
`WRITE`, or `UNBOX`, that returns a Java string will convert the returned string
to a Ruby string.

A call from a foreign language to Ruby using `NEW`, `EXECUTE`, `INVOKE`, or
`WRITE`, that has Java strings as arguments, will convert each Java string
argument to a Ruby string.

A call from a foreign language to Ruby `NEW`, `EXECUTE`, `INVOKE` or `READ`
that returns a Ruby string will not convert it to a Java string, as this would
break our C extension support which uses these messages to get Ruby objects
and expects to be able to mutate them and so on
(compare with `Truffle::Interop.execute_without_conversion`).

It is planned that Java strings, and boxed foreign strings (foreign objects that
respond positively to `IS_BOXED` and `UNBOX` to a Java string), will be able to
be used in all locations where a Ruby string could, and will be converted to a
Ruby string at that point, but this is not implemented yet.

## Import and export

`Truffle::Interop.export(:name, value)`

`Truffle::Interop.export_method(:name)` (looks for `name` in `Object`)

`value = Truffle::Interop.import(:name)`

`Truffle::Interop.import_method(:name)` (defines `name` in `Object`)

## Interop Eval

`Truffle::Interop.eval(mime_type, source)`

`Truffle::Interop.import_file(path)` evals an entire file, guessing the correct
language MIME type.

## Java interop

`Truffle::Interop.java_type(name)` returns a Java class object, given a name
such as `java.lang.Integer` or `int[]`.

`java_class.new(*args)` creates a new instance of a Java class object.

`Truffle::Interop.java_type_name(java_class)` gives the Java class name for a
Java class object, or `nil` if the object is not a Java class object.

`Truffle::Interop.from_java_array(array)` creates a shallow copy of a Java
array as a Ruby array.

## Additional methods

`Truffle::Interop.foreign?(object)`

`Truffle::Interop.mime_type_supported?(mime_type)` reports if a language's MIME
type is supported for interop.

`Truffle::Interop.java_string?(object)`

`Truffle::Interop.to_java_string(ruby_string)`

`Truffle::Interop.from_java_string(java_string)`

`Truffle::Interop.enumerable(object)` gives you an `Enumerable` interface to a
foreign object.

`Truffle::Interop.to_java_array(array)` gives you a proxied Java array copied
from the Ruby array.

`Truffle::Interop.java_array(a, b, c...)` a literal variant of the former.

`Truffle::Interop.deproxy(object)` deproxy a Java object if it has been proxied.

`Truffle::Interop.to_array(object)` converts to a Ruby array by calling
`GET_SIZE` and sending `READ` for each index from zero to the size.

`Truffle::Interop.meta_object(object)` returns the Truffle meta-object that
describes the object (unrelated to the metaclass).

## Notes on method resolution

Method calls on foreign objects are usually translated exactly into foreign
`READ`, `INVOKE` and other messages. The other methods listed in
[what messages are sent for Ruby syntax on foreign objects](#what-messages-are-sent-for-ruby-syntax-on-foreign-objects)
are a kind of special-form - they are implemented as a special case in the
call-site logic. They are not being provided by `BasicObject` or `Kernel` as you
may expect. This means that for example `#method` isn't available, and you can't
use it to get the method for `#to_a` on a foreign object, as it is a
special-form, not a method.

Interop ignores visibility entirely.
