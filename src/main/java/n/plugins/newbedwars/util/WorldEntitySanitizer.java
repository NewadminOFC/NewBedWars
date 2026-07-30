package n.plugins.newbedwars.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * Removes ArmorStand entity records directly from Anvil region files.
 *
 * <p>This class deliberately has no Bukkit or NMS dependency. It runs on the
 * clone I/O executor before Bukkit opens the copied world, so holograms from
 * distant chunks never need to be loaded on the main server thread.</p>
 */
public final class WorldEntitySanitizer {

    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = SECTOR_BYTES * 2;
    private static final int LOCATION_COUNT = 1024;
    private static final int MAX_NBT_COLLECTION_LENGTH = 64 * 1024 * 1024;
    private static final int MAX_NBT_DEPTH = 64;
    private static final byte[] ZERO_SECTOR = new byte[SECTOR_BYTES];

    private WorldEntitySanitizer() {
    }

    public static Result sanitizeArmorStands(File worldFolder) throws IOException {
        if (worldFolder == null || !worldFolder.isDirectory()) {
            throw new IOException("Pasta do mundo invalida para sanitizacao.");
        }

        List<File> regionFiles = new ArrayList<File>();
        collectRegionFiles(worldFolder, regionFiles, 0);

        Result result = new Result();
        for (File regionFile : regionFiles) {
            sanitizeRegion(regionFile, result);
        }
        return result;
    }

    private static void collectRegionFiles(File directory, List<File> output, int depth) {
        if (directory == null || output == null || depth > 8
            || !directory.isDirectory() || Files.isSymbolicLink(directory.toPath())) {
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        boolean regionDirectory = "region".equalsIgnoreCase(directory.getName());
        for (File child : children) {
            if (child == null || Files.isSymbolicLink(child.toPath())) {
                continue;
            }
            if (regionDirectory && child.isFile() && child.getName().toLowerCase(Locale.ENGLISH).endsWith(".mca")) {
                output.add(child);
            } else if (child.isDirectory()) {
                collectRegionFiles(child, output, depth + 1);
            }
        }
    }

    private static void sanitizeRegion(File regionFile, Result result) throws IOException {
        result.regionsScanned++;
        if (regionFile.length() < HEADER_BYTES) {
            result.failedRegions++;
            return;
        }

        ChunkRecord[] records = new ChunkRecord[LOCATION_COUNT];
        boolean changed = false;
        try (RandomAccessFile input = new RandomAccessFile(regionFile, "r")) {
            int[] locations = new int[LOCATION_COUNT];
            int[] timestamps = new int[LOCATION_COUNT];
            for (int index = 0; index < LOCATION_COUNT; index++) {
                locations[index] = input.readInt();
            }
            for (int index = 0; index < LOCATION_COUNT; index++) {
                timestamps[index] = input.readInt();
            }

            for (int index = 0; index < LOCATION_COUNT; index++) {
                int location = locations[index];
                int sectorOffset = location >>> 8;
                int sectorCount = location & 0xFF;
                if (sectorOffset < 2 || sectorCount <= 0) {
                    continue;
                }

                long byteOffset = (long) sectorOffset * SECTOR_BYTES;
                long allocatedBytes = (long) sectorCount * SECTOR_BYTES;
                if (byteOffset < HEADER_BYTES || byteOffset + allocatedBytes > input.length()
                    || allocatedBytes > Integer.MAX_VALUE) {
                    result.failedChunks++;
                    continue;
                }

                byte[] rawSectors = new byte[(int) allocatedBytes];
                input.seek(byteOffset);
                input.readFully(rawSectors);
                ChunkRecord record = new ChunkRecord(rawSectors, timestamps[index]);
                records[index] = record;
                result.chunksScanned++;

                try {
                    int removed = sanitizeChunk(record);
                    if (removed > 0) {
                        result.armorStandsRemoved += removed;
                        changed = true;
                    }
                } catch (IOException exception) {
                    result.failedChunks++;
                } catch (RuntimeException exception) {
                    result.failedChunks++;
                }
            }
        }

        if (!changed) {
            return;
        }

        rewriteRegion(regionFile, records);
        result.regionsRewritten++;
    }

    private static int sanitizeChunk(ChunkRecord record) throws IOException {
        DataInputStream recordInput = new DataInputStream(new ByteArrayInputStream(record.rawSectors));
        int declaredLength = recordInput.readInt();
        if (declaredLength <= 1 || declaredLength > record.rawSectors.length - 4) {
            throw new IOException("Comprimento de chunk invalido.");
        }

        int compressionType = recordInput.readUnsignedByte();
        byte[] compressedPayload = new byte[declaredLength - 1];
        recordInput.readFully(compressedPayload);
        byte[] nbtPayload = decompress(compressedPayload, compressionType);
        NbtRoot root = readRoot(nbtPayload);
        int removed = removeArmorStands(root);
        if (removed <= 0) {
            return 0;
        }

        byte[] rewrittenNbt = writeRoot(root);
        byte[] rewrittenPayload = compress(rewrittenNbt, compressionType);
        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream(rewrittenPayload.length + 5);
        DataOutputStream output = new DataOutputStream(recordBytes);
        output.writeInt(rewrittenPayload.length + 1);
        output.writeByte(compressionType);
        output.write(rewrittenPayload);
        output.flush();
        record.rewrittenRecord = recordBytes.toByteArray();
        return removed;
    }

    private static byte[] decompress(byte[] payload, int compressionType) throws IOException {
        InputStream source = new ByteArrayInputStream(payload);
        InputStream compressed;
        if (compressionType == 1) {
            compressed = new GZIPInputStream(source);
        } else if (compressionType == 2) {
            compressed = new InflaterInputStream(source);
        } else if (compressionType == 3) {
            compressed = source;
        } else {
            throw new IOException("Compressao de chunk desconhecida: " + compressionType);
        }

        try (BufferedInputStream input = new BufferedInputStream(compressed);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_NBT_COLLECTION_LENGTH) {
                    throw new IOException("NBT do chunk excede o limite seguro.");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] compress(byte[] payload, int compressionType) throws IOException {
        if (compressionType == 3) {
            return payload;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream compressed = compressionType == 1
            ? new GZIPOutputStream(bytes)
            : new DeflaterOutputStream(bytes);
        try {
            compressed.write(payload);
        } finally {
            compressed.close();
        }
        return bytes.toByteArray();
    }

    private static void rewriteRegion(File regionFile, ChunkRecord[] records) throws IOException {
        File parent = regionFile.getParentFile();
        File temporary = new File(parent, regionFile.getName() + ".nbw-sanitize-" + System.nanoTime() + ".tmp");
        int[] rewrittenLocations = new int[LOCATION_COUNT];
        int currentSector = 2;
        long lastModified = regionFile.lastModified();

        try {
            try (RandomAccessFile output = new RandomAccessFile(temporary, "rw")) {
                output.setLength(HEADER_BYTES);
                for (int index = 0; index < LOCATION_COUNT; index++) {
                    ChunkRecord record = records[index];
                    if (record == null) {
                        continue;
                    }

                    byte[] data = record.rewrittenRecord == null ? record.rawSectors : record.rewrittenRecord;
                    int sectors = (data.length + SECTOR_BYTES - 1) / SECTOR_BYTES;
                    if (sectors <= 0 || sectors > 255 || currentSector > 0xFFFFFF - sectors) {
                        throw new IOException("Arquivo de regiao excedeu o limite Anvil.");
                    }

                    rewrittenLocations[index] = (currentSector << 8) | sectors;
                    output.seek((long) currentSector * SECTOR_BYTES);
                    output.write(data);
                    writePadding(output, (sectors * SECTOR_BYTES) - data.length);
                    currentSector += sectors;
                }

                output.seek(0L);
                for (int location : rewrittenLocations) {
                    output.writeInt(location);
                }
                for (ChunkRecord record : records) {
                    output.writeInt(record == null ? 0 : record.timestamp);
                }
                output.setLength((long) currentSector * SECTOR_BYTES);
                output.getFD().sync();
            }

            try {
                Files.move(temporary.toPath(), regionFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), regionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (lastModified > 0L) {
                regionFile.setLastModified(lastModified);
            }
        } finally {
            if (temporary.exists()) {
                Files.deleteIfExists(temporary.toPath());
            }
        }
    }

    private static void writePadding(RandomAccessFile output, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int amount = Math.min(remaining, ZERO_SECTOR.length);
            output.write(ZERO_SECTOR, 0, amount);
            remaining -= amount;
        }
    }

    private static NbtRoot readRoot(byte[] payload) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        byte type = input.readByte();
        if (type == 0) {
            throw new IOException("NBT raiz vazio.");
        }
        String name = input.readUTF();
        return new NbtRoot(name, new NbtTag(type, readPayload(input, type, 0)));
    }

    private static byte[] writeRoot(NbtRoot root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(root.tag.type);
        output.writeUTF(root.name);
        writePayload(output, root.tag, 0);
        output.flush();
        return bytes.toByteArray();
    }

    private static Object readPayload(DataInputStream input, byte type, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) {
            throw new IOException("NBT excedeu a profundidade segura.");
        }

        switch (type) {
            case 1:
                return Byte.valueOf(input.readByte());
            case 2:
                return Short.valueOf(input.readShort());
            case 3:
                return Integer.valueOf(input.readInt());
            case 4:
                return Long.valueOf(input.readLong());
            case 5:
                return Float.valueOf(input.readFloat());
            case 6:
                return Double.valueOf(input.readDouble());
            case 7: {
                int length = readLength(input, "byte array");
                byte[] value = new byte[length];
                input.readFully(value);
                return value;
            }
            case 8:
                return input.readUTF();
            case 9: {
                byte elementType = input.readByte();
                int length = readLength(input, "lista");
                NbtList list = new NbtList(elementType);
                for (int index = 0; index < length; index++) {
                    list.values.add(new NbtTag(elementType, readPayload(input, elementType, depth + 1)));
                }
                return list;
            }
            case 10: {
                Map<String, NbtTag> compound = new LinkedHashMap<String, NbtTag>();
                while (true) {
                    byte childType;
                    try {
                        childType = input.readByte();
                    } catch (EOFException exception) {
                        throw new IOException("Compound NBT incompleto.", exception);
                    }
                    if (childType == 0) {
                        break;
                    }
                    String childName = input.readUTF();
                    compound.put(childName, new NbtTag(childType, readPayload(input, childType, depth + 1)));
                }
                return compound;
            }
            case 11: {
                int length = readLength(input, "int array");
                int[] value = new int[length];
                for (int index = 0; index < length; index++) {
                    value[index] = input.readInt();
                }
                return value;
            }
            case 12: {
                int length = readLength(input, "long array");
                long[] value = new long[length];
                for (int index = 0; index < length; index++) {
                    value[index] = input.readLong();
                }
                return value;
            }
            default:
                throw new IOException("Tipo NBT desconhecido: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream output, NbtTag tag, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) {
            throw new IOException("NBT excedeu a profundidade segura.");
        }

        switch (tag.type) {
            case 1:
                output.writeByte(((Byte) tag.value).byteValue());
                return;
            case 2:
                output.writeShort(((Short) tag.value).shortValue());
                return;
            case 3:
                output.writeInt(((Integer) tag.value).intValue());
                return;
            case 4:
                output.writeLong(((Long) tag.value).longValue());
                return;
            case 5:
                output.writeFloat(((Float) tag.value).floatValue());
                return;
            case 6:
                output.writeDouble(((Double) tag.value).doubleValue());
                return;
            case 7: {
                byte[] value = (byte[]) tag.value;
                output.writeInt(value.length);
                output.write(value);
                return;
            }
            case 8:
                output.writeUTF((String) tag.value);
                return;
            case 9: {
                NbtList list = (NbtList) tag.value;
                output.writeByte(list.elementType);
                output.writeInt(list.values.size());
                for (NbtTag child : list.values) {
                    writePayload(output, child, depth + 1);
                }
                return;
            }
            case 10: {
                Map<String, NbtTag> compound = (Map<String, NbtTag>) tag.value;
                for (Map.Entry<String, NbtTag> entry : compound.entrySet()) {
                    output.writeByte(entry.getValue().type);
                    output.writeUTF(entry.getKey());
                    writePayload(output, entry.getValue(), depth + 1);
                }
                output.writeByte(0);
                return;
            }
            case 11: {
                int[] value = (int[]) tag.value;
                output.writeInt(value.length);
                for (int item : value) {
                    output.writeInt(item);
                }
                return;
            }
            case 12: {
                long[] value = (long[]) tag.value;
                output.writeInt(value.length);
                for (long item : value) {
                    output.writeLong(item);
                }
                return;
            }
            default:
                throw new IOException("Tipo NBT desconhecido: " + tag.type);
        }
    }

    private static int readLength(DataInputStream input, String description) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_NBT_COLLECTION_LENGTH) {
            throw new IOException("Tamanho invalido em " + description + ": " + length);
        }
        return length;
    }

    @SuppressWarnings("unchecked")
    private static int removeArmorStands(NbtRoot root) {
        if (root == null || root.tag == null || root.tag.type != 10) {
            return 0;
        }

        Map<String, NbtTag> rootCompound = (Map<String, NbtTag>) root.tag.value;
        NbtTag levelTag = rootCompound.get("Level");
        Map<String, NbtTag> level = levelTag != null && levelTag.type == 10
            ? (Map<String, NbtTag>) levelTag.value
            : rootCompound;
        NbtTag entitiesTag = level.get("Entities");
        if (entitiesTag == null || entitiesTag.type != 9) {
            return 0;
        }

        NbtList entities = (NbtList) entitiesTag.value;
        int removed = 0;
        java.util.Iterator<NbtTag> iterator = entities.values.iterator();
        while (iterator.hasNext()) {
            NbtTag entity = iterator.next();
            if (isArmorStand(entity)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private static boolean isArmorStand(NbtTag entity) {
        if (entity == null || entity.type != 10) {
            return false;
        }
        Map<String, NbtTag> compound = (Map<String, NbtTag>) entity.value;
        NbtTag id = compound.get("id");
        if (id == null || id.type != 8 || !(id.value instanceof String)) {
            return false;
        }

        String normalized = ((String) id.value).toLowerCase(Locale.ENGLISH)
            .replace("minecraft:", "")
            .replace("_", "");
        return "armorstand".equals(normalized);
    }

    public static final class Result {
        private int regionsScanned;
        private int regionsRewritten;
        private int chunksScanned;
        private int armorStandsRemoved;
        private int failedChunks;
        private int failedRegions;

        public int getRegionsScanned() {
            return regionsScanned;
        }

        public int getRegionsRewritten() {
            return regionsRewritten;
        }

        public int getChunksScanned() {
            return chunksScanned;
        }

        public int getArmorStandsRemoved() {
            return armorStandsRemoved;
        }

        public int getFailedChunks() {
            return failedChunks;
        }

        public int getFailedRegions() {
            return failedRegions;
        }

        public boolean hasFailures() {
            return failedChunks > 0 || failedRegions > 0;
        }
    }

    private static final class ChunkRecord {
        private final byte[] rawSectors;
        private final int timestamp;
        private byte[] rewrittenRecord;

        private ChunkRecord(byte[] rawSectors, int timestamp) {
            this.rawSectors = rawSectors;
            this.timestamp = timestamp;
        }
    }

    private static final class NbtRoot {
        private final String name;
        private final NbtTag tag;

        private NbtRoot(String name, NbtTag tag) {
            this.name = name;
            this.tag = tag;
        }
    }

    private static final class NbtTag {
        private final byte type;
        private final Object value;

        private NbtTag(byte type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class NbtList {
        private final byte elementType;
        private final List<NbtTag> values = new ArrayList<NbtTag>();

        private NbtList(byte elementType) {
            this.elementType = elementType;
        }
    }
}
