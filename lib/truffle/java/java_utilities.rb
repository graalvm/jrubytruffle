###### BEGIN LICENSE BLOCK ######
# Version: EPL 1.0/GPL 2.0/LGPL 2.1
#
# The contents of this file are subject to the Common Public
# License Version 1.0 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of
# the License at http://www.eclipse.org/legal/cpl-v10.html
#
# Software distributed under the License is distributed on an "AS
# IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
# implied. See the License for the specific language governing
# rights and limitations under the License.
#
# Alternatively, the contents of this file may be used under the terms of
# either of the GNU General Public License Version 2 or later (the "GPL"),
# or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
# in which case the provisions of the GPL or the LGPL are applicable instead
# of those above. If you wish to allow use of your version of this file only
# under the terms of either the GPL or the LGPL, and not to allow others to
# use your version of this file under the terms of the EPL, indicate your
# decision by deleting the provisions above and replace them with the notice
# and other provisions required by the GPL or the LGPL. If you do not delete
# the provisions above, a recipient may use your version of this file under
# the terms of any one of the EPL, the GPL or the LGPL.
###### END LICENSE BLOCK ######

# @private
module JavaUtilities

  Java = ::Truffle::Interop::Java
  Interop = ::Truffle::Interop

  module ::Truffle::Interop::Java

    @@loader = nil

    def self.loader
      @@loader
    end

    def self.loader= a_loader
      @@loader = a_loader
    end

    def self.invoke_java_method(method, *args)
      e = catch :java do
        return invoke_with_catch(method, :java, *args)
      end
      raise JavaException.new(JavaUtilities.wrap_java_value(e))
    end

    def self.java_class_by_name(a_name)
      java_class_by_name_and_loader(a_name, loader)
    end
  end

  def self.extend_proxy(java_class_name, &block)
    java_class = JavaUtilities.get_proxy_class(java_class_name)
    java_class.class_eval(&block)
  end

  def self.print_class(java_type, indent="")
    while !java_type.nil? && java_type.name != "java.lang.Class"
      puts "#{indent}Name:  #{java_type.name}, access: #{ JavaUtilities.access(java_type) }  Interfaces: "
      java_type.interfaces.each { |i| print_class(i, "  #{indent}") }
      puts "#{indent}SuperClass: "
      print_class(java_type.superclass, "  #{indent}")
      java_type = java_type.superclass
    end
  end

  def self.get_proxy_class(name)
    get_package_or_class(name, true, true)
  end

  def self.get_package_module_dot_format(name)
    get_package_or_class(name, false)
  end

  def self.get_class_full_name(name)
    get_package_or_class(name, true)
  end

  def self.get_relative_package_or_class(parent, name)
    probably_class = (name[0] == name[0].upcase)
    full_name = "#{parent}.#{name}"
    get_package_or_class(full_name, probably_class)
  end

  def self.get_inner_class(a_class, inner_name)
    outer_name = Interop.from_java_string(
      Java.invoke_java_method(CLASS_GET_NAME, unwrap_java_value(a_class)))
    self.get_package_or_class("#{outer_name}$#{inner_name}", true)
  end

  def self.get_package_or_class(name, probably_class, must_be_class=false)
    a_class = begin
                Java.java_class_by_name(name)
              rescue Exception
                nil
              end
    if !probably_class
      # Probably not true, but somebody may have been silly in their class names.
      return make_proxy(a_class) if a_class != nil

      return ::Java::JavaPackage.new(*name.split(".")) unless must_be_class
    else
      if a_class == nil
        raise NameError, "Missing class name ('#{name}')"
      end

      return make_proxy(a_class)
    end
  end

  # Classes we'll need to bootstrap things.
  JAVA_CLASS_CLASS = Java.java_class_by_name("java.lang.Class")
  JAVA_CLASS_ARRAY = Java.java_class_by_name("[Ljava.lang.Class;")
  JAVA_CONSTRUCTOR_CLASS = Java.java_class_by_name("java.lang.reflect.Constructor")
  JAVA_CONSTRUCTOR_ARRAY = Java.java_class_by_name("[Ljava.lang.reflect.Constructor;")
  JAVA_FIELD_CLASS = Java.java_class_by_name("java.lang.reflect.Field")
  JAVA_FIELD_ARRAY = Java.java_class_by_name("[Ljava.lang.reflect.Field;")
  JAVA_METHOD_CLASS = Java.java_class_by_name("java.lang.reflect.Method")
  JAVA_METHOD_ARRAY = Java.java_class_by_name("[Ljava.lang.reflect.Method;")
  JAVA_MODIFIERS_CLASS = Java.java_class_by_name("java.lang.reflect.Modifier")
  JAVA_OBJECT_CLASS = Java.java_class_by_name("java.lang.Object")
  JAVA_OBJECT_ARRAY = Java.java_class_by_name("[Ljava.lang.Object;")
  JAVA_PACKAGE_CLASS = Java.java_class_by_name("java.lang.Package")
  JAVA_STRING_CLASS = Java.java_class_by_name("java.lang.String")
  JAVA_THROWABLE_CLASS = Java.java_class_by_name("java.lang.Throwable")

  # We'll also need the primitive numeric classes, but can't get those by name.
  CLASS_GET_FIELD = Java.get_java_method(
    JAVA_CLASS_CLASS, "getField", false, JAVA_FIELD_CLASS, JAVA_STRING_CLASS)
  FIELD_GET = Java.get_java_method(
    JAVA_FIELD_CLASS, "get", false, JAVA_OBJECT_CLASS, JAVA_OBJECT_CLASS)
  JAVA_BOOLEAN_CLASS = Java.java_class_by_name("java.lang.Boolean")
  JAVA_PRIM_BOOLEAN_CLASS = Java.invoke_java_method(
    FIELD_GET, Java.invoke_java_method(
      CLASS_GET_FIELD, JAVA_BOOLEAN_CLASS, Interop.to_java_string("TYPE")),
    nil)

  JAVA_INTEGER_CLASS = Java.java_class_by_name("java.lang.Integer")
  JAVA_PRIM_INT_CLASS = Java.invoke_java_method(
    FIELD_GET, Java.invoke_java_method(
      CLASS_GET_FIELD, JAVA_INTEGER_CLASS, Interop.to_java_string("TYPE")),
    nil)

  JAVA_LONG_CLASS = Java.java_class_by_name("java.lang.Long")
  JAVA_PRIM_LONG_CLASS = Java.invoke_java_method(
    FIELD_GET, Java.invoke_java_method(
      CLASS_GET_FIELD, JAVA_LONG_CLASS, Interop.to_java_string("TYPE")),
    nil)

  JAVA_DOUBLE_CLASS = Java.java_class_by_name("java.lang.Double")
  JAVA_PRIM_DBL_CLASS = Java.invoke_java_method(
    FIELD_GET, Java.invoke_java_method(
      CLASS_GET_FIELD, JAVA_DOUBLE_CLASS, Interop.to_java_string("TYPE")),
    nil)

  # We'll also want the modifiers for methods and fields.
  module Modifiers
    names = ["ABSTRACT",
             "FINAL",
             "INTERFACE",
             "NATIVE",
             "PRIVATE",
             "PROTECTED",
             "PUBLIC",
             "STATIC",
             "STRICT",
             "SYNCHRONIZED",
             "TRANSIENT",
             "VOLATILE" ]
    names.each do |s|
      js = Interop.to_java_string(s)
      val = Java.invoke_java_method(
        FIELD_GET,
        Java.invoke_java_method(
          CLASS_GET_FIELD, JAVA_MODIFIERS_CLASS, js), nil)
      const_set(s, val)
    end
  end

  # Methods we'll need to bootstrap stuff.
  CLASS_GET_CONSTRUCTORS = Java.get_java_method(
    JAVA_CLASS_CLASS, "getConstructors", false, JAVA_CONSTRUCTOR_ARRAY)
  CLASS_GET_DECLARED_FIELDS = Java.get_java_method(
    JAVA_CLASS_CLASS, "getDeclaredFields", false, JAVA_FIELD_ARRAY)
  CLASS_GET_DECLARED_METHODS = Java.get_java_method(
    JAVA_CLASS_CLASS, "getDeclaredMethods", false, JAVA_METHOD_ARRAY)
  CLASS_GET_ENCLOSING_CLASS = Java.get_java_method(
    JAVA_CLASS_CLASS, "getEnclosingClass", false, JAVA_CLASS_CLASS)
  CLASS_GET_INTERFACES = Java.get_java_method(
    JAVA_CLASS_CLASS, "getInterfaces", false, JAVA_CLASS_ARRAY)
  CLASS_GET_NAME = Java.get_java_method(
    JAVA_CLASS_CLASS, "getName", false, JAVA_STRING_CLASS)
  CLASS_GET_PACKAGE = Java.get_java_method(
    JAVA_CLASS_CLASS, "getPackage", false, JAVA_PACKAGE_CLASS)
  CLASS_GET_SIMPLE_NAME = Java.get_java_method(
    JAVA_CLASS_CLASS, "getSimpleName", false, JAVA_STRING_CLASS)
  CLASS_GET_SUPER_CLASS = Java.get_java_method(
    JAVA_CLASS_CLASS, "getSuperclass", false, JAVA_CLASS_CLASS)
  CLASS_IS_ARRAY = Java.get_java_method(
    JAVA_CLASS_CLASS, "isArray", false, JAVA_PRIM_BOOLEAN_CLASS)
  CLASS_IS_INTERFACE = Java.get_java_method(
    JAVA_CLASS_CLASS, "isInterface", false, JAVA_PRIM_BOOLEAN_CLASS)
  CLASS_IS_MEMBER_CLASS = Java.get_java_method(
    JAVA_CLASS_CLASS, "isMemberClass", false, JAVA_PRIM_BOOLEAN_CLASS)
  CLASS_IS_PRIMITIVE = Java.get_java_method(
    JAVA_CLASS_CLASS, "isPrimitive", false, JAVA_PRIM_BOOLEAN_CLASS)
  FIELD_GET_MODIFIERS = Java.get_java_method(
    JAVA_FIELD_CLASS, "getModifiers", false, JAVA_PRIM_INT_CLASS)
  FIELD_GET_NAME = Java.get_java_method(
    JAVA_FIELD_CLASS, "getName", false, JAVA_STRING_CLASS)
  METHOD_GET_MODIFIERS = Java.get_java_method(
    JAVA_METHOD_CLASS, "getModifiers", false, JAVA_PRIM_INT_CLASS)
  METHOD_GET_NAME = Java.get_java_method(
    JAVA_METHOD_CLASS, "getName", false, JAVA_STRING_CLASS)
  OBJECT_GET_CLASS = Java.get_java_method(
    JAVA_OBJECT_CLASS, "getClass", false, JAVA_CLASS_CLASS)
  PACKAGE_GET_NAME = Java.get_java_method(
    JAVA_PACKAGE_CLASS, "getName", false, JAVA_STRING_CLASS)

  def self.make_proxy(a_class)
    was_in_make_proxy = Thread.current[:MAKING_JAVA_PROXY]
    begin
      Thread.current[:MAKING_JAVA_PROXY] = true

      a_proxy = PROXIES[a_class]

      return a_proxy if a_proxy != nil

      parent = ensure_owner(a_class)

      a_proxy = if Java.invoke_java_method(CLASS_IS_INTERFACE, a_class)
                  make_interface_proxy(a_class)
                elsif Java.invoke_java_method(CLASS_IS_ARRAY, a_class)
                  make_array_proxy(a_class)
                else
                  make_object_proxy(a_class)
                end

      # Setting up the parent classes may have caused the child to be
      # bootstrapped. Consider class A with a static public final field
      # of B and class B which extends A. To create the proxy for B we
      # must first create the parent A, but it then must create the
      # proxy for B to wrap its constant value. This situation is
      # resolved as follows
      #
      # 1. Start to create proxy for B (proxy 1).
      # 2. Start to create proxy for A (proxy 2).
      # 3. Add A's proxy to PROXIES.
      # 4. Add static fields to A.
      # 5. Start to create proxy for B (proxy 3).
      # 6. Add B's proxy to PROXIES
      # 7. Finish creating proxy for B.(proxy 3).
      # 8. Finish creating proxy for A.(proxy 2).
      # 9. Don't set const since PROXIES now contains a case for B (proxy 3).

      a_proxy.java_class = a_class

      existing_proxy = PROXIES.put_if_absent(a_class, a_proxy)

      if existing_proxy == nil

        JavaProxyBuilder.new(a_proxy, a_class).build

        # Not all proxies can be added as constants.
        begin
          parent.const_set(
            Interop.from_java_string(
              Java.invoke_java_method(CLASS_GET_SIMPLE_NAME, a_class)),
            a_proxy) unless parent == nil
        rescue
        end
      else
        a_proxy = existing_proxy
      end

      a_proxy
    ensure
      Thread.current[:MAKING_JAVA_PROXY] = was_in_make_proxy
    end
  end

  def self.ensure_owner(a_class)
    if Java.invoke_java_method(CLASS_IS_MEMBER_CLASS, a_class)
      package = make_proxy(Java.invoke_java_method(CLASS_GET_ENCLOSING_CLASS, a_class))
    else
      package = Java.invoke_java_method(CLASS_GET_PACKAGE, a_class)
      if package != nil
        name = Interop.from_java_string(
          Java.invoke_java_method(PACKAGE_GET_NAME, package))
        ::Java::JavaPackage.new(*name.split("."))
      else
        ::Java
      end
    end
  end

  def self.make_array_proxy(a_class)
    a_proxy = Class.new(ArrayJavaProxy)
    # This is done here to break the circle in bootstrapping.
    a_proxy
  end

  def self.make_interface_proxy(a_class)
    included_method = lambda { |class_or_module|
      unless Thread.current[:MAKING_JAVA_PROXY]
        case class_or_module
        when Class
          unless class_or_module.instance_variable_defined?(:@java_interfaces)
            interfaces = class_or_module.instance_variable_set(:@java_interfaces, [])
          else
            interfaces = class_or_module.instance_variable_get(:@java_interfaces)
          end

          interfaces << self
          java_class = class_or_module.instance_variable_set(
            :@java_class, JavaUtilities.make_java_proxy_class(*interfaces))

          # We know a proxy will have a public constructor.
          con = JavaUtilities::unreflect_constructor(
            JavaUtilities::java_array_get(Java.invoke_java_method(CLASS_GET_CONSTRUCTORS, java_class), 0))

          constructor = lambda { Java.invoke_java_method(con, JavaUtilities::invocation_handler(self)) }

          class_or_module.class_eval do
            include JavaProxyMethods unless ancestors.include?(JavaProxyMethods)
            attr_accessor :java_object

            define_method(:__build_java_proxy__, constructor)

            def self.java_class
              @java_class
            end

            def self.new(*rest)
              res = self.allocate
              res.instance_variable_set(
                :@java_object,
                res.__build_java_proxy__)
              res.__send__(:initialize, *rest)
              res
            end

            def hashCode
              hash
            end

            def toString
              to_s
            end

          end
        when Module
          class_or_module.__send__(:define_singleton_method, :included, included_method)
        else
          raise TypeError, "#{self} unexpectedly included in #{class_or_module}."
        end
      end
    }

    a_proxy = Module.new do
      define_singleton_method(:included, included_method)
      class << self
        attr_accessor :java_class
        def java_class
          JavaUtilities::wrap_java_value(@java_class)
        end

        def const_missing(name)
          JavaUtilities.get_inner_class(self.java_class, name)
        end
      end
    end
    a_proxy
  end

  def self.wrap_java_value(val)
    if val.kind_of?(Truffle::Interop::Foreign)
      a_class = get_java_class(val)
      if Java.java_refs_equal?(a_class, JAVA_STRING_CLASS)
        return Interop.from_java_string(val)
      end
      wrapped_val = make_proxy(a_class).allocate
      wrapped_val.java_object = val
      return wrapped_val
    end
    val
  end

  def self.unwrap_java_value(val)
    case val
    when String
      Interop.to_java_string(val)
    when Hash
      HashProxy.new(val).java_object
    when JavaProxyMethods
      val.java_object
    else
      val
    end
  end

  JAVA_LOOKUP_CLASS = Java.java_class_by_name("java.lang.invoke.MethodHandles$Lookup")
  JAVA_METHODHANDLE_CLASS = Java.java_class_by_name("java.lang.invoke.MethodHandle")
  JAVA_METHODHANDLES_CLASS = Java.java_class_by_name("java.lang.invoke.MethodHandles")

  LOOKUP_UNREFLECT = Java.get_java_method(
    JAVA_LOOKUP_CLASS, "unreflect", false, JAVA_METHODHANDLE_CLASS, JAVA_METHOD_CLASS)
  LOOKUP_UNREFLECT_CONSTRUCTOR = Java.get_java_method(
    JAVA_LOOKUP_CLASS, "unreflectConstructor", false, JAVA_METHODHANDLE_CLASS, JAVA_CONSTRUCTOR_CLASS)
  LOOKUP_UNREFLECT_GETTER = Java.get_java_method(
    JAVA_LOOKUP_CLASS, "unreflectGetter", false, JAVA_METHODHANDLE_CLASS, JAVA_FIELD_CLASS)
  LOOKUP_UNREFLECT_SETTER = Java.get_java_method(
    JAVA_LOOKUP_CLASS, "unreflectSetter", false, JAVA_METHODHANDLE_CLASS, JAVA_FIELD_CLASS)

  # We can't use the public lookup because it won't resolve caller sensitive methods.
  LOOKUP = Java.get_lookup

  def self.unreflect_method(a_method)
    begin
      Java.invoke_java_method(LOOKUP_UNREFLECT, LOOKUP, a_method)
    rescue Exception => exception
      nil
    end
  end

  def self.unreflect_constructor(a_constructor)
    begin
      Java.invoke_java_method(LOOKUP_UNREFLECT_CONSTRUCTOR, LOOKUP, a_constructor)
    rescue Exception => exception
      nil
    end
  end

  def self.unreflect_getter(a_field)
    begin
      Java.invoke_java_method(LOOKUP_UNREFLECT_GETTER, LOOKUP, a_field)
    rescue Exception
      nil
    end
  end

  def self.unreflect_setter(a_field)
    begin
      Java.invoke_java_method(LOOKUP_UNREFLECT_SETTER, LOOKUP, a_field)
    rescue Exception
      nil
    end
  end

  REFLECT_ARRAY_CLASS = Java.java_class_by_name("java.lang.reflect.Array")
  REFLECT_ARRAY_LENGTH = Java.get_java_method(
    REFLECT_ARRAY_CLASS, "getLength", true, JAVA_PRIM_INT_CLASS, JAVA_OBJECT_CLASS)

  ARRAY_GETTER_GETTER = Java.get_java_method(
    JAVA_METHODHANDLES_CLASS, "arrayElementGetter", true, JAVA_METHODHANDLE_CLASS, JAVA_CLASS_CLASS)
  ARRAY_SETTER_GETTER = Java.get_java_method(
    JAVA_METHODHANDLES_CLASS, "arrayElementSetter", true, JAVA_METHODHANDLE_CLASS, JAVA_CLASS_CLASS)
  ARRAY_GETTER = Java.invoke_java_method(ARRAY_GETTER_GETTER, JAVA_OBJECT_ARRAY)

  def self.java_array_size(an_array)
    Java.invoke_java_method(REFLECT_ARRAY_LENGTH, an_array)
  end

  def self.java_array_get(an_array, index)
    Java.invoke_java_method(ARRAY_GETTER, an_array, index)
  end

  def self.get_java_class(obj)
    Java.invoke_java_method(OBJECT_GET_CLASS, obj)
  end

  def self.constant_field?(a_field)
    modifiers = Java.invoke_java_method(FIELD_GET_MODIFIERS, a_field)
    constant = Modifiers::FINAL | Modifiers::PUBLIC | Modifiers::STATIC
    constant == modifiers & constant
  end

  def self.static_field?(a_field)
    modifiers = Java.invoke_java_method(FIELD_GET_MODIFIERS, a_field)
    modifiers == modifiers | Modifiers::STATIC
  end

  def self.final_field?(a_field)
    modifiers = Java.invoke_java_method(FIELD_GET_MODIFIERS, a_field)
    modifiers == modifiers | Modifiers::FINAL
  end

  def self.static_method?(a_method)
    modifiers = Java.invoke_java_method(METHOD_GET_MODIFIERS, a_method)
    modifiers == modifiers | Modifiers::STATIC
  end

  def self.make_object_proxy(a_class)
    super_class = if Java.java_refs_equal?(a_class, JAVA_OBJECT_CLASS)
                    ConcreteJavaProxy
                  else
                    make_proxy(Java.invoke_java_method(CLASS_GET_SUPER_CLASS, a_class))
                  end
    a_proxy = Class.new(super_class)
  end

  def self.make_java_proxy_class(*modules)
    java_interfaces = modules.map { |m| unwrap_java_value(m.java_class) }
    Java.proxy_class(Java.loader, *java_interfaces)
  end

  def self.invocation_handler(owner)
    Java.invocation_handler(invoker_proc(owner))
  end

  def self.invoker_proc(owner)
    lambda{ |m, *args|
      args = args.map { |a| JavaUtilities::wrap_java_value(a) }
      m = JavaUtilities::wrap_java_value(m)
      JavaUtilities.unwrap_java_value(owner.__send__(m.name, *args))
    }
  end
end
