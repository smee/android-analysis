import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import com.googlecode.dex2jar.ClassVisitorFactory;
import com.googlecode.dex2jar.reader.DexFileReader;
import com.googlecode.dex2jar.v3.V3;
import com.googlecode.dex2jar.v3.V3AccessFlagsAdapter;

/**
 * 
 */

/**
 * @author sdienst
 *
 */
public class Dex2Jar {
	public static byte[] doData(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ZipOutputStream zos = new ZipOutputStream(baos);

        DexFileReader reader = new DexFileReader(data);
        V3AccessFlagsAdapter afa = new V3AccessFlagsAdapter();
        reader.accept(afa);
        reader.accept(new V3(afa.getAccessFlagsMap(), afa.getInnerNameMap(), new ClassVisitorFactory() {
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
                            e.printStackTrace();
                        }
                    }
                };
            }
        }));
        zos.finish();
        zos.close();
        return baos.toByteArray();
    }

}
