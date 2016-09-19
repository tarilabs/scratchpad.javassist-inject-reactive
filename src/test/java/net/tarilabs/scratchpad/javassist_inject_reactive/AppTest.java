package net.tarilabs.scratchpad.javassist_inject_reactive;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.drools.core.phreak.ReactiveList;
import org.drools.core.phreak.ReactiveObject;
import org.drools.core.phreak.ReactiveObjectUtil;
import org.drools.core.spi.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtField.Initializer;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFileWriter.MethodWriter;
import javassist.bytecode.SignatureAttribute.ClassType;
import javassist.bytecode.SignatureAttribute.MethodSignature;
import javassist.bytecode.SignatureAttribute.TypeArgument;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.stackmap.MapMaker;
import my.DroolsPojo;

public class AppTest {
    private static final String DROOLS_PREFIX = "$$_drools_";
    private static final String FIELD_WRITER_PREFIX = DROOLS_PREFIX + "write_";
    private static final String DROOLS_LIST_OF_TUPLES = DROOLS_PREFIX + "lts";

    public static final Logger LOG = LoggerFactory.getLogger(AppTest.class);
     

    
    private Map<String, CtMethod> writeMethods = new HashMap<String, CtMethod>();
    
    private ClassPool cp;
    private CtClass ReactiveObjectCtClass; 
    private CtClass ListCtClass;
    private String droolsCorePath;
    
    @Before
    public void init() throws Exception {
//        URL url = new File("target/classes/").toURI().toURL();
//        System.out.println(url);
//        ClassLoader cl = URLClassLoader.newInstance(new URL[]{url}, null);
//        cl.loadClass(DroolsPojo.class.getName());
        
        // demonstrating use in current ClassLoader.
        ClassLoader.getSystemClassLoader().loadClass(DroolsPojo.class.getName());
        new DroolsPojo("", 22);
        
        ClassPool parent = ClassPool.getDefault();
        
        String aname = ReactiveObject.class.getPackage().getName().replaceAll("\\.", "/") + "/" +  ReactiveObject.class.getSimpleName()+".class";
        System.out.println(aname);
        String apath = ClassLoader.getSystemClassLoader().getResource( aname).getPath();
        System.out.println( apath );
        String path = apath.substring(0, apath.indexOf("!"));
        System.out.println( path );
        
        File f = new File(new URI(path));
        System.out.println( f.exists() );
        System.out.println(f.getAbsolutePath());
        
        droolsCorePath = f.getAbsolutePath();
        
        ClassPool child = new ClassPool(null);
        child.appendSystemPath();
        child.appendClassPath(f.getAbsolutePath());
        cp = child;
        
        ReactiveObjectCtClass = cp.get(ReactiveObject.class.getName());
        ListCtClass = cp.get(List.class.getName());
    }
    
    @Test
    public void test2() throws Exception {
        CtClass droolsPojo = cp.get("my.DroolsPojo");
        
        droolsPojo.addInterface(ReactiveObjectCtClass);
        
        CtField ltsCtField = new CtField(ListCtClass, DROOLS_LIST_OF_TUPLES, droolsPojo);
        ltsCtField.setModifiers(Modifier.PRIVATE);
        ClassType listOfTuple = new SignatureAttribute.ClassType(List.class.getName(),
                new TypeArgument[]{new TypeArgument( new SignatureAttribute.ClassType(Tuple.class.getName()) )});
        ltsCtField.setGenericSignature(
                listOfTuple.encode()
                );
        // Do not use the Initializer.byNew... as those method always pass at least 1 parameter which is "this".
        droolsPojo.addField(ltsCtField, Initializer.byExpr("new java.util.ArrayList();"));
        
        final CtMethod getLeftTuplesCtMethod = CtNewMethod.make(
                "public java.util.List getLeftTuples() {\n" + 
                "    return this.$$_drools_lts;\n" + 
                "}", droolsPojo );
        MethodSignature getLeftTuplesSignature = new MethodSignature(null, null, listOfTuple, null);
        getLeftTuplesCtMethod.setGenericSignature(getLeftTuplesSignature.encode());
        droolsPojo.addMethod(getLeftTuplesCtMethod);
        
        final CtMethod addLeftTupleCtMethod = CtNewMethod.make(
                "public void addLeftTuple("+Tuple.class.getName()+" leftTuple) {\n" + 
                "    if ($$_drools_lts == null) {\n" + 
                "        $$_drools_lts = new java.util.ArrayList();\n" + 
                "    }\n" + 
                "    $$_drools_lts.add(leftTuple);\n" + 
                "}", droolsPojo );
        droolsPojo.addMethod(addLeftTupleCtMethod);
        
        for (CtField f : collectReactiveFields(droolsPojo)) {
            System.out.println(f);
            writeMethods.put(f.getName(), makeWriter(droolsPojo, f));
        }
        
        enhanceAttributesAccess(droolsPojo);
        
        // first call toClass before the original class is loaded, it will persist the bytecode instrumentation changes in the classloader.
        droolsPojo.writeFile("target/JAVASSIST");
        
        URL url = new File("target/classes/").toURI().toURL();
        System.out.println(url);
        ClassLoader cl = URLClassLoader.newInstance(new URL[]{url, new File(droolsCorePath).toURI().toURL()}, null);
        droolsPojo.toClass(cl, Class.class.getProtectionDomain());
        
        System.out.println("--- in default classloader: ");
        Arrays.stream(DroolsPojo.class.getMethods()).forEach(System.out::println);
        
        new DroolsPojo("", 1);
        
        System.out.println("--- using 'isolated' classloader: ");
        Class<?> droolsPojoInIsolatedCL = cl.loadClass(DroolsPojo.class.getName());
        Arrays.stream(droolsPojoInIsolatedCL.getMethods()).forEach(System.out::println);
        
    }
    
    protected void enhanceAttributesAccess(CtClass managedCtClass) throws Exception {
        final ConstPool constPool = managedCtClass.getClassFile().getConstPool();
        final ClassPool classPool = managedCtClass.getClassPool();

        for ( Object oMethod : managedCtClass.getClassFile().getMethods() ) {
            final MethodInfo methodInfo = (MethodInfo) oMethod;
            final String methodName = methodInfo.getName();

            // skip methods added by enhancement and abstract methods (methods without any code)
            if ( methodName.startsWith( DROOLS_PREFIX ) || methodInfo.getCodeAttribute() == null ) {
                continue;
            }

            try {
                final CodeIterator itr = methodInfo.getCodeAttribute().iterator();
                while ( itr.hasNext() ) {
                    final int index = itr.next();
                    final int op = itr.byteAt( index );
                    if ( op != Opcode.PUTFIELD && op != Opcode.GETFIELD ) {
                        continue;
                    }

//                    // only transform access to fields of the entity being enhanced
//                    if ( !managedCtClass.getName().equals( constPool.getFieldrefClassName( itr.u16bitAt( index + 1 ) ) ) ) {
//                        continue;
//                    }

                    final String fieldName = constPool.getFieldrefName( itr.u16bitAt( index + 1 ) );
                    
                    if (!collectReactiveFields(managedCtClass).stream().map(ct->ct.getName()).filter(n->n.equals(fieldName)).findAny().isPresent() ) {
                        continue;
                    }


//                    if ( op == Opcode.GETFIELD ) {
//                        final int methodIndex = MethodWriter.addMethod( constPool, attributeMethods.getReader() );
//                        itr.writeByte( Opcode.INVOKEVIRTUAL, index );
//                        itr.write16bit( methodIndex, index + 1 );
//                    } else
                    if (op == Opcode.PUTFIELD) {
                        // addMethod is a safe add, if constant already present it return the existing value without adding.
                        final int methodIndex = addMethod( constPool, writeMethods.get(fieldName) );
                        itr.writeByte( Opcode.INVOKEVIRTUAL, index );
                        itr.write16bit( methodIndex, index + 1 );
                    }
                }
                methodInfo.getCodeAttribute().setAttribute( MapMaker.make( classPool, methodInfo ) );
            }
            catch (BadBytecode bb) {
                final String msg = String.format(
                        "Unable to perform field access transformation in method [%s]",
                        methodName
                );
                throw new Exception( msg, bb );
            }
        }
    }
    
    private static CtMethod write(CtClass target, String format, Object ... args) throws CannotCompileException {
        final String body = String.format( format, args );
        System.out.printf( "writing method into [%s]:%n%s%n", target.getName(), body );
        final CtMethod method = CtNewMethod.make( body, target );
        System.err.println(body);
        target.addMethod( method );
        return method;
    }
    
    /**
     * Add Method to ConstPool. If method was not in the ConstPool will add and return index, otherwise will return index of already existing entry of constpool
     */
    private static int addMethod(ConstPool cPool, CtMethod method) {
        // addMethodrefInfo is a safe add, if constant already present it return the existing value without adding.
        return cPool.addMethodrefInfo( cPool.getThisClassInfo(), method.getName(), method.getSignature() );
    }
    
    private CtMethod makeWriter(CtClass managedCtClass, CtField field) throws Exception {
        final String fieldName = field.getName();
        final String writerName = FIELD_WRITER_PREFIX + fieldName;
        
        return write(
                managedCtClass,
                "public void %s(%s %s) {%n%s%n}",
                writerName,
                field.getType().getName(),
                fieldName,
                buildWriteInterceptionBodyFragment( field )
        );
        
    }

    private String buildWriteInterceptionBodyFragment(CtField field) throws NotFoundException {
        // remember: In the source text given to setBody(), the identifiers starting with $ have special meaning
        // $0, $1, $2, ...     this and actual parameters 
        System.out.println(field.getType().getClass() + " " + field.getType());
        
        if ( field.getType().subtypeOf( ListCtClass ) ) {
            return String.format(
                    "  this.%1$s = new "+ReactiveList.class.getName()+"($1); ",
                    field.getName()
                    );
        }
        
        // 2nd line will result in: ReactiveObjectUtil.notifyModification((ReactiveObject) this);
        // and that is fine because ASM: INVOKESTATIC org/drools/core/phreak/ReactiveObjectUtil.notifyModification (Lorg/drools/core/phreak/ReactiveObject;)V
        return String.format(
                "  this.%1$s = $1;%n" +
                "  "+ReactiveObjectUtil.class.getName()+".notifyModification($0); ",
                field.getName()
                );
    }

    private static List<CtField> collectReactiveFields(CtClass managedCtClass) {
        final List<CtField> persistentFieldList = new ArrayList<CtField>();
        for ( CtField ctField : managedCtClass.getDeclaredFields() ) {
            // skip static fields and skip fields added by enhancement
            if ( Modifier.isStatic( ctField.getModifiers() ) || ctField.getName().startsWith( DROOLS_PREFIX ) ) {
                continue;
            }
            // skip outer reference in inner classes
            if ( "this$0".equals( ctField.getName() ) ) {
                continue;
            }
            persistentFieldList.add( ctField );
        }
        // CtClass.getFields() does not return private fields, while CtClass.getDeclaredFields() does not return inherit
        for ( CtField ctField : managedCtClass.getFields() ) {
            if ( ctField.getDeclaringClass().equals( managedCtClass ) ) {
                // Already processed above
                continue;
            }
            if ( Modifier.isStatic( ctField.getModifiers() ) || ctField.getName().startsWith( DROOLS_PREFIX ) ) {
                continue;
            }
            persistentFieldList.add( ctField );
        }
        return persistentFieldList;
    }
    
}