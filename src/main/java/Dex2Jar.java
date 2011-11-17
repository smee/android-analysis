import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import com.googlecode.dex2jar.Method;
import com.googlecode.dex2jar.ir.ET;
import com.googlecode.dex2jar.reader.DexFileReader;
import com.googlecode.dex2jar.util.ASMifierCodeV;
import com.googlecode.dex2jar.util.Escape;
import com.googlecode.dex2jar.util.Out;
import com.googlecode.dex2jar.v3.ClassVisitorFactory;
import com.googlecode.dex2jar.v3.V3;
import com.googlecode.dex2jar.v3.V3AccessFlagsAdapter;
import com.googlecode.dex2jar.visitors.DexClassVisitor;
import com.googlecode.dex2jar.visitors.DexCodeVisitor;
import com.googlecode.dex2jar.visitors.DexMethodVisitor;
import com.googlecode.dex2jar.visitors.EmptyVisitor;

/**
 * 
 */

/**
 * @author sdienst
 *
 */
public class Dex2Jar {

	 public static byte[] doData(byte[] data) throws IOException {

	        DexFileReader reader = new DexFileReader(data);
	        V3AccessFlagsAdapter afa = new V3AccessFlagsAdapter();
	        reader.accept(afa, DexFileReader.SKIP_CODE | DexFileReader.SKIP_DEBUG);
	        
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        final Map<Method, Exception> exceptions = new HashMap<Method, Exception>();
	        
	        final ZipOutputStream zos = new ZipOutputStream(baos);
	        try {
	            reader.accept(new V3(afa.getAccessFlagsMap(), afa.getInnerNameMap(), afa.getExtraMember(), exceptions,
	                    new ClassVisitorFactory() {
	                        public ClassVisitor create(final String name) {
	                            return new ClassWriter(ClassWriter.COMPUTE_MAXS) {
	                                /*
	                                 * (non-Javadoc)
	                                 * 
	                                 * @see org.objectweb.asm.ClassWriter#visitEnd()
	                                 */
	                                @Override
	                                public void visitEnd() {
	                                    super.visitEnd();
	                                    try {
	                                        byte[] data = this.toByteArray();
	                                        ZipEntry entry = new ZipEntry(name + ".class");
	                                        zos.putNextEntry(entry);
	                                        zos.write(data);
	                                        zos.closeEntry();
	                                    } catch (IOException e) {
	                                        e.printStackTrace(System.err);
	                                    }
	                                }
	                            };
	                        }
	                    }), DexFileReader.SKIP_DEBUG);
	            zos.finish();
	        } catch (Exception e) {
	            e.printStackTrace(System.err);
	        } finally {
	            zos.close();
	        }

	        if (exceptions != null && exceptions.size() > 0) {

	            for (Map.Entry<Method, Exception> e : exceptions.entrySet()) {
	                System.err.println("Error:" + e.getKey().toString() + "->" + e.getValue().getMessage());
	            }
	            File errorFile = File.createTempFile("dex2jar", "error.txt");
	            final PrintWriter fw = new PrintWriter(new OutputStreamWriter(FileUtils.openOutputStream(errorFile),
	                    "UTF-8"));
	            fw.println(getVersionString());
	            final Out out = new Out() {

	                public void s(String format, Object... arg) {
	                    fw.println(String.format(format, arg));
	                }

	                public void s(String s) {
	                    fw.println(s);
	                }

	                public void push() {

	                }

	                public void pop() {

	                }
	            };
	            reader.accept(new EmptyVisitor() {

	                @Override
	                public DexClassVisitor visit(int accessFlags, String className, String superClass,
	                        String[] interfaceNames) {
	                    return new EmptyVisitor() {

	                        @Override
	                        public DexMethodVisitor visitMethod(final int accessFlags, final Method method) {
	                            if (exceptions.containsKey(method)) {
	                                return new EmptyVisitor() {

	                                    @Override
	                                    public DexCodeVisitor visitCode() {
	                                        out.s("===========================================");
	                                        Exception exception = exceptions.get(method);
	                                        exception.printStackTrace(fw);
	                                        out.s("");
	                                        out.s("DexMethodVisitor mv=cv.visitMethod(%s, %s);",
	                                                Escape.methodAcc(accessFlags), Escape.v(method));
	                                        out.s("DexCodeVisitor code = mv.visitCode();");
	                                        return new ASMifierCodeV(out);
	                                    }

	                                    @Override
	                                    public void visitEnd() {
	                                        out.s("mv.visitEnd();");
	                                        fw.flush();
	                                    }
	                                };
	                            }
	                            return null;
	                        }

	                    };
	                }

	            }, DexFileReader.SKIP_DEBUG);

	            fw.close();
	            System.err.println("Detail Error Information in File " + errorFile);
	            System.err.println("Please report this file to http://code.google.com/p/dex2jar/issues/entry if possible.");
	        }
	        return baos.toByteArray();
	    }
	    public static String getVersionString() {
	        return "dex2jar version: reader-" + DexFileReader.class.getPackage().getImplementationVersion()
	                + ", translator-" + Dex2Jar.class.getPackage().getImplementationVersion() + ", ir-"
	                + ET.class.getPackage().getImplementationVersion();
	    }

}
