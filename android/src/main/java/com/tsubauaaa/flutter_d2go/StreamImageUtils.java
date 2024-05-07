package com.tsubauaaa.flutter_d2go;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import io.flutter.plugin.common.MethodCall;

/**
 * Utility class to convert bitmaps from camera stream image and metadata.
 */
public class StreamImageUtils {

    private final Context context;
    private final HashMap<String, Object> imageMap;

    /**
     * Constructor to initialize imageMap and context of member variables.
     *
     * @param call    Method call called from Flutter. Contains various arguments.
     * @param context Used in renderscript.
     */
    public StreamImageUtils(@NonNull MethodCall call, @NonNull Context context) {
        this.context = context;
        ArrayList<byte[]> imageBytesList = call.argument("imageBytesList");
        ArrayList<Integer> imageBytesPerPixel = call.argument("imageBytesPerPixel");
        int width = call.argument("width");
        int height = call.argument("height");
        int rotation = call.argument("rotation");
        this.imageMap = new HashMap<>();
        ArrayList<Map<String, Object>> planes = new ArrayList<>(Arrays.asList(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>()));
        for (int i = 0; i < planes.size(); i++) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("bytes", imageBytesList.get(i));
            value.put("bytesPerPixel", imageBytesPerPixel.get(i));
            planes.set(i, value);
        }

        imageMap.put("planes", planes);
        imageMap.put("width", width);
        imageMap.put("height", height);
        imageMap.put("rotation", rotation);
    }

    /**
     * Convert to Bitmap for inferring from camera stream image and metadata (imageMap).
     *
     * @param inputWidth  Width size for inference image resizing.
     * @param inputHeight Height size for inference image resizing.
     * @return Bitmap for inference converted from camera stream image and metadata (imageMap)
     */
    public Bitmap getBitmap(int inputWidth, int inputHeight) {
        Bitmap bitmap = Bitmap.createScaledBitmap(streamImageToBitmap(), inputWidth, inputHeight, true);

        Matrix matrix = new Matrix();
        matrix.postRotate((int) imageMap.get("rotation"));
        return Bitmap.createBitmap(bitmap, 0, 0, inputWidth, inputHeight, matrix, true);
    }

    /**
     * Convert stream image and metadata (imageMap) to byte[] in YUV420 NV21 format and then convert to Bitmap.
     *
     * @return Bitmap converted from stream image and metadata (imageMap).
     */
    private Bitmap streamImageToBitmap() {
        RenderScript rs = RenderScript.create(context);

        int width = (int) imageMap.get("width");
        int height = (int) imageMap.get("height");

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        byte[] data = cameraStreamToBytes();
        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(data.length);
        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
        in.copyFrom(data);

        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);

        out.copyTo(bitmap);
        rs.finish();
        return bitmap;
    }

    /**
     * Convert camera stream image and metadata (imageMap) to YUV420 NV21 format byte[].
     *
     * @return YUV420 NV21 format byte [] converted from camera stream image and metadata (imageMap).
     */
    private byte[] cameraStreamToBytes() {
        int width = (int) imageMap.get("width");
        int height = (int) imageMap.get("height");

        ArrayList<Map<String, Object>> planes = (ArrayList<Map<String, Object>>) imageMap.get("planes");
        byte[] yBytes = (byte[]) planes.get(0).get("bytes");
        byte[] uBytes = (byte[]) planes.get(1).get("bytes");
        byte[] vBytes = (byte[]) planes.get(2).get("bytes");
        final int colorPixelStride = (int) planes.get(1).get("bytesPerPixel");

        ByteBuffer yBuffer = ByteBuffer.wrap(yBytes);
        ByteBuffer uBuffer = ByteBuffer.wrap(uBytes);
        ByteBuffer vBuffer = ByteBuffer.wrap(vBytes);

        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        try {
            outputBytes.write(yBuffer.array());
            outputBytes.write(vBuffer.array());
            outputBytes.write(uBuffer.array());
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] data = outputBytes.toByteArray();
        final int dataSize = width * height;
        final int chromaSize = dataSize / 4;
        final int totalSize = dataSize + 2 * chromaSize;
        for (int i = dataSize; i < totalSize; i += 2) {
            data[dataSize + i / 2] = vBuffer.get(i);
            data[dataSize + i / 2 + 1] = uBuffer.get(i + 1);
        }
        return data;
    }

}
