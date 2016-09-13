package net.tarilabs.scratchpad.javassist_inject_reactive;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFileWriter.MethodWriter;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import javassist.bytecode.stackmap.MapMaker;

public class AppTest {
    public static final Logger LOG = LoggerFactory.getLogger(AppTest.class);
    private static final String PERSISTENT_FIELD_WRITER_PREFIX = "$$_drools_write_";
    
    private Map<String, CtMethod> writeMethods = new HashMap<String, CtMethod>();
    
    @Test
    public void test2() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass droolsPojo = cp.get("my.DroolsPojo");
        for (CtField f : droolsPojo.getDeclaredFields()) {
            System.out.println(f);
            writeMethods.put(f.getName(), makeWriter(droolsPojo, f));
        }
        
        enhanceAttributesAccess(droolsPojo);
        
        droolsPojo.writeFile("target/JAVASSIST");
    }
    
    protected void enhanceAttributesAccess(CtClass managedCtClass) throws Exception {
        final ConstPool constPool = managedCtClass.getClassFile().getConstPool();
        final ClassPool classPool = managedCtClass.getClassPool();

        for ( Object oMethod : managedCtClass.getClassFile().getMethods() ) {
            final MethodInfo methodInfo = (MethodInfo) oMethod;
            final String methodName = methodInfo.getName();

            // skip methods added by enhancement and abstract methods (methods without any code)
            if ( methodName.startsWith( "$$_drools_" ) || methodInfo.getCodeAttribute() == null ) {
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

                    // only transform access to fields of the entity being enhanced
                    if ( !managedCtClass.getName().equals( constPool.getFieldrefClassName( itr.u16bitAt( index + 1 ) ) ) ) {
                        continue;
                    }

                    final String fieldName = constPool.getFieldrefName( itr.u16bitAt( index + 1 ) );


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
    
    private CtMethod makeWriter(CtClass managedCtClass,
            CtField field) throws Exception {
        final String fieldName = field.getName();
        final String writerName = PERSISTENT_FIELD_WRITER_PREFIX + fieldName;
        
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
        System.out.println(field.getType().getClass() + " " + field.getType());
        if ( field.getType().subtypeOf(ClassPool.getDefault().get(java.util.List.class.getCanonicalName())) ) {
            return String.format(
                    "  this.%1$s = $1;%n" +
                    "  System.out.println(\"That was a list\"); ",
                    field.getName()
                    );
        }
        return String.format(
                "  this.%1$s = $1;%n" +
                "  System.out.println(\"x\"); ",
                field.getName()
                );
    }

    private static CtField[] collectPersistentFields(CtClass managedCtClass) {
        final List<CtField> persistentFieldList = new LinkedList<CtField>();
        for ( CtField ctField : managedCtClass.getDeclaredFields() ) {
            // skip static fields and skip fields added by enhancement
            if ( Modifier.isStatic( ctField.getModifiers() ) || ctField.getName().startsWith( "$$_hibernate_" ) ) {
                continue;
            }
            // skip outer reference in inner classes
            if ( "this$0".equals( ctField.getName() ) ) {
                continue;
            }
            persistentFieldList.add( ctField );
        }
        // HHH-10646 Add fields inherited from @MappedSuperclass
        // CtClass.getFields() does not return private fields, while CtClass.getDeclaredFields() does not return inherit
        for ( CtField ctField : managedCtClass.getFields() ) {
            if ( ctField.getDeclaringClass().equals( managedCtClass ) ) {
                // Already processed above
                continue;
            }
            if ( Modifier.isStatic( ctField.getModifiers() ) ) {
                continue;
            }
            persistentFieldList.add( ctField );
        }
        return persistentFieldList.toArray(new CtField[]{});
    }
    
}