package com.cb2495.verityconfig.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 只读图片文件头取出宽高，不做完整解码。
 * <p>帮助界面的图片改成后台解码之后，版面必须在图片还没解码出来时就算好
 * （否则占位框会先小后大、来回跳），所以尺寸要提前拿到。读文件头只要几百
 * 字节，而完整解码一张 1600×720 的截图要分配好几 MB 内存，代价差了三个
 * 数量级——这也是能把尺寸读取留在主线程、只把解码挪走的原因。
 * <p>只识别帮助资源里实际用到的 PNG 与 JPEG，其它格式返回 {@code null}。
 */
public final class ImageHeader {

    private ImageHeader() {}

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    /** 尺寸上限，用来挡掉解析错位产生的离谱数值。 */
    private static final int MAX_DIMENSION = 100_000;

    /**
     * 读取图片宽高。
     *
     * @param in 图片输入流，方法内部会把它包成带缓冲的流并关闭
     * @return {@code {宽度, 高度}}；无法识别或读取失败时返回 {@code null}
     */
    public static int[] readSize(InputStream in) {
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
            data.mark(PNG_SIGNATURE.length);
            byte[] head = new byte[PNG_SIGNATURE.length];
            data.readFully(head);
            if (isPng(head)) {
                return readPngSize(data);
            }
            data.reset();
            return readJpegSize(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPng(byte[] head) {
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (head[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    /** PNG：签名之后是 IHDR 块，长度 4 字节 + 类型 4 字节 + 宽 4 字节 + 高 4 字节。 */
    private static int[] readPngSize(DataInputStream in) throws IOException {
        in.skipBytes(4);          // IHDR 数据长度
        in.skipBytes(4);          // "IHDR"
        int width = in.readInt();
        int height = in.readInt();
        return valid(width, height) ? new int[]{width, height} : null;
    }

    /**
     * JPEG：按标记段往后走，直到遇到 SOF（帧开始）段落。
     * <p>SOF 段里依次是长度(2)、精度(1)、高(2)、宽(2)。
     */
    private static int[] readJpegSize(DataInputStream in) throws IOException {
        // SOI
        if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) return null;

        while (true) {
            int marker;
            do {
                marker = in.readUnsignedByte();
            } while (marker != 0xFF);            // 跳过填充字节

            int type;
            do {
                type = in.readUnsignedByte();
            } while (type == 0xFF);              // 标记允许重复 0xFF

            // 这些标记后面没有长度字段，直接看下一个
            if (type == 0x01 || (type >= 0xD0 && type <= 0xD8)) continue;
            // EOI 或 SOS：再往后不会再出现帧头，尺寸信息已经错过了
            if (type == 0xD9 || type == 0xDA) return null;

            int length = in.readUnsignedShort();
            if (isStartOfFrame(type)) {
                in.skipBytes(1);                 // 采样精度
                int height = in.readUnsignedShort();
                int width = in.readUnsignedShort();
                return valid(width, height) ? new int[]{width, height} : null;
            }
            in.skipBytes(length - 2);
        }
    }

    /** SOF0~SOF3、SOF5~SOF7、SOF9~SOF11、SOF13~SOF15（不含 DHT/JPG 等同段号的标记）。 */
    private static boolean isStartOfFrame(int type) {
        return (type >= 0xC0 && type <= 0xC3)
                || (type >= 0xC5 && type <= 0xC7)
                || (type >= 0xC9 && type <= 0xCB)
                || (type >= 0xCD && type <= 0xCF);
    }

    private static boolean valid(int width, int height) {
        return width > 0 && height > 0
                && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
    }
}
