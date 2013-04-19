/**
 * 
 * Use case: you have some unstructured input -- maybe user input, maybe even untrusted
 * user input, and you want to easily instantiate pojo's with this. This is the promise
 * of java beans, but all the getters and setters are a pain in the neck, and you may
 * not want to expose everything that has a getter or a setter to this user input.
 * 
 * I also use it for loading content from a db (e.g. org.json.simple creates Maps from
 * json) if another application is creating the content so I don't want to just serialize
 * the object.
 * 
 * This allows you to:
 * (a) configure an object using raw fields or getters/setters
 * (b) instantiate objects of unknown types/classes
 * (c) all the while, whitelisting what you're using user input for
 * 
 * =====
 * 
 * annotation for a user-settable field or method. you can use
 * the internal class Setter to set the user-settable fields
 * from a hashmap of String key-values.
 *
 * e.g. if you have
 * package mypackage;
 * @UserSettable class MyClass {
 *   Float f1 = 1.0F;
 *   @UserSettable Float f2 = 1.0F;
 *   Float f3 = 1.0F;
 *   @UserSettable float f4 = 1.0F;
 *   @UserSettable setThird(float f) {
 *     f3 = f;
 *   }
 *   setF4(float f) {
 *     f4 = f;
 *   }
 * }
 * you can use
 * UserSettable.Setter.instantiate( { class: "mypackage.MyClass", f1: 2.0, f2: 2.0, third: 2.0, f4: 2.0} )
 * this will
 *  - instantiate the class using the no-parameter constructor
 *  - fail to set f1, it's not accessible
 *  - set f2 directly by accessing the field
 *  - set f3 using the setThird accessor
 *  - set f4 using the accessor, because the field is marked accessible
 *
 * cool, right?
 *
 * assumes use of FuzzyBoolean https://github.com/kstinchcombe/fuzzyboolean
 * 
 * @author kai
 *
 */