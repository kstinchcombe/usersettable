import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;

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

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface UserSettable {

    static Logger logger = Logger.getRootLogger();
	
    /**
     *
     * @author kai
     *
     */
    public static class Setter {


        /**
         * instantiate an object based on the set of params. assumes you have a
         *  fully-qualified class name as the key "class"
         * Note: the class name you supply has to also be annotated UserSettable, or we
         * refuse to instantiate it.
         * @param params
         * @return the object
         */
        public static <T> T instantiate(Map<String, String> params) {
            return instantiate(params, null, null);
        }
        /**
         * instantiate an object based on the set of params. assumes EITHER:
         *  - you have a fully-qualified class name as the key "class"
         *  - you have a simple class name in class, and it's in the package defaultPackage
         *  - you have a defaultClass specified, which is instantiated if class is not supplied
         * Note: the class name you supply has to also be annotated UserSettable, or we
         * refuse to instantiate it. The default doesn't have to be annotated, you did that
         * explicitly in your code.
         * @param params
         * @param defaultClass
         * @param defaultPackage
         * @return the object
         */
        public static <T> T instantiate(Map<String, String> params, Class defaultClass, String defaultPackage) {
            T retVal = null;
            Class objectClass = null;
            
            try {

                // determine the class

                if (params.containsKey("class")) {
                    String className = params.get("class");
                    if (-1 == className.indexOf(".")) {
                        if (null == defaultPackage) {
                            logger.error("", new IllegalArgumentException("UserSettable.Setter: default package not supplied, and class name "+className+" from params is not qualified"));
                        }
                        else {
                            className = defaultPackage + "." + className;
                        }
                    }
                    try {
                        objectClass = Class.forName(className);
                        if (null == objectClass.getAnnotation(UserSettable.class)) {
                            logger.error("", new IllegalArgumentException("UserSettable.Setter: objectClass "+objectClass.getName()+" was not annotated UserSettable, we refuse to instantiate it!"));
                            objectClass = null;
                        }
                    } catch (Exception e) {
                        logger.error("UserSettable.Setter: objectClass "+className+" could not be found or was not accessible! We will try the default class.", e);
                    }
                }
                if (null == objectClass && null != defaultClass) {
                    objectClass = defaultClass;
                }
                if (null == objectClass) {
                    logger.error("", new IllegalArgumentException("UserSettable.Setter: default class not supplied, and params does not contain a class name"));
                    return null;
                }

                // instantiate the class

                retVal = (T)objectClass.newInstance();
                setAll(retVal, params);

            }
            catch(Exception e) {
                logger.error("", new RuntimeException("UserSettable.Setter: could not instantiate, or could not set some fields. object class is "+ (null == objectClass ? "null" : objectClass.getName()), e));
            }
            return retVal;
        }

        /**
         * sets all values on the object, using fields directly or getter/setter
         * methods, only if the field or setter is annotated UserSettable
         * @param o the object to operate on
         * @param params the key-value pairs to set on the object
         */
        public static void setAll(Object o, Map<String, String> params) {

            Class objectClass = o.getClass();

            // set the legal values by looping through the keys

            for (String key : params.keySet()) {

                if ("class".equals(key)) {continue;}

                try {

                    Field field = null;                 // the field we're going to set
                    Method setter = null;               // if we have found a setter method
                    Class type = null;                  // what type we need to cast our input to
                    boolean isUserSettable = false;     // if we have found the annotation enabling us to set it

                    // look for an underlying field
                    try {
                        field = objectClass.getField(key);
                        type = field.getType();
                        if (null != field.getAnnotation(UserSettable.class)) {
                            isUserSettable = true;
                        }
                    } catch (Exception e) {}    //ok if this fails, there just isn't a field

                    // look for a setter method with correct parameter types
                    if (null != type) {
                        for (Method m : objectClass.getMethods()) {
                            if (m.getName().toLowerCase().equals(("set"+key).toLowerCase()) // named like a setter
                                    && m.getParameterTypes().length == 1    // takes one parameter
                                    && (isUserSettable || (null != m.getAnnotation(UserSettable.class)))    // is accessible to us
                                    && m.getParameterTypes()[0].equals(type)    // parameter is of the correct type
                                    ) {
                                setter = m;
                                isUserSettable = isUserSettable || null != m.getAnnotation(UserSettable.class);
                            }
                        }
                    }

                    // fail over to looking for a setter with incorrect parameter types
                    if (null == type) {
                        for (Method m : objectClass.getMethods()) {
                            if (m.getName().toLowerCase().equals(("set"+key).toLowerCase()) // named like a setter
                                    && m.getParameterTypes().length == 1    // takes one parameter
                                    && (isUserSettable || (null != m.getAnnotation(UserSettable.class)))    // is accessible to us
                                    ) {
                                // check for uh oh, two name-overloaded single-parameter setters, we don't know which to use!
                                if (null != setter) {
                                    logger.error("", new IllegalArgumentException("UserSettable.Setter: can't have name-overloaded setter method in class "+objectClass.getName()+" for value "+key.toLowerCase()));
                                }
                                setter = m;
                                type = m.getParameterTypes()[0];
                                isUserSettable = isUserSettable || null != m.getAnnotation(UserSettable.class);
                            }
                        }
                    }

                    // see that we found something
                    // if type is set, we have either a field or a setter
                    if (null == type) {
                        if (!"body".equals(key)) {  // we have a lot of these, gets annoying...
                            throw new IllegalArgumentException("Couldn't find a field or setter for field name "+key+" in class "+objectClass.getName());
                        }
                        continue;
                    }

                    // see if there are permissions on the field or the accessor
                    if (!isUserSettable) {
                        throw new IllegalArgumentException("Neither the field nor any accessor for "+key+" in class "+objectClass.getName()+" is annotated with UserSettable");
                    }

                    // cast as the correct type
                    String value = params.get(key);
                    Object arg;
                    if (type == Integer.TYPE || type == Integer.class) {
                        arg = Integer.parseInt(value);
                    }
                    else if (type == Long.TYPE || type == Long.class) {
                        arg = Long.parseLong(value);
                    }
                    else if (type == Boolean.TYPE || type == Boolean.class) {
                        arg = FuzzyBoolean.sParse(value, type == Boolean.class ? null : false);
                    }
                    else if (type == Float.TYPE || type == Float.class) {
                        arg = Float.parseFloat(value);
                    }
                    else if (type == String.class) {
                        arg = value;
                    }
                    else if (type.isEnum()) {
                        arg = Enum.valueOf(type, value.toUpperCase());
                    }
                    else if (type == java.util.Date.class) {
                        SimpleDateFormat dateInputFormat = new SimpleDateFormat("yyyy-MM-dd");
                        arg =  dateInputFormat.parse(value);
                    }
                    else {
                        throw new IllegalArgumentException("Couldn't cast the input to a "+type.getName()+" field name "+key+" in class "+objectClass.getName());
                    }

                    // invoke the setter, if we have one
                    if (null != setter) {
                        if (setter.getParameterTypes()[0].equals(String.class)) {
                            setter.invoke(o, "" + arg);
                        }
                        else {
                            setter.invoke(o, arg);
                        }
                    }
                    // or set the field directly
                    else {
                        field.set(o, arg);
                    }

                } catch (Exception e) {
                    logger.error(String.format("Couldn't map key %s to %s field: %s", key, objectClass.getName(), e.getMessage()));
                }   // end try-catch block

            }   // end field loop

        }   // end method

    }   // end internal class

}   // end interface

