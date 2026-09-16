package com.yamo.snorelab;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Single-writer append journal; immutable gzip parts plus independent upload receipts. */
public final class DevDatasetStore implements Closeable {
    public static final int SCHEMA = 1;
    public static final long PART_BYTES = 4L * 1024 * 1024;
    public final File dir;
    private final JSONObject manifest;
    private FileOutputStream raw;
    private long seq, count, firstSeq, firstWall, lastWall, firstMono, lastMono;
    private int part;
    private long nextHour, lastWallTick, lastMonoTick;
    private final long partLimit;
    private String zoneId;

    public DevDatasetStore(File dir, JSONObject initial, long wall, long mono) throws Exception {
        this(dir, initial, wall, mono, PART_BYTES);
    }
    DevDatasetStore(File dir, JSONObject initial, long wall, long mono, long partLimit) throws Exception {
        this.dir = dir;
        this.partLimit = partLimit;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("dataset_directory_failed");
        File mf = new File(dir, "manifest.json");
        manifest = mf.isFile() ? readJson(mf) : initial;
        if (manifest == null) throw new IOException("dataset_manifest_missing");
        part = 0; seq = 0;
        for (File d : descriptors(dir)) {
            JSONObject m = readJson(d);
            part = Math.max(part, m.getInt("part") + 1);
            seq = Math.max(seq, m.getLong("lastSeq") + 1);
        }
        // A committed gzip descriptor wins over a leftover journal after a crash.
        File[] openFiles = dir.listFiles((d,n) -> n.matches("part_[0-9]{6}\\.open"));
        if (openFiles != null) for (File f : openFiles) {
            int i = Integer.parseInt(f.getName().substring(5,11));
            if (new File(dir, name(i) + ".meta.json").isFile()) Files.deleteIfExists(f.toPath());
            else { if (i != part) throw new IOException("dataset_part_gap"); recoverJournal(f); }
        }
        zoneId=TimeZone.getDefault().getID();
        nextHour = nextHour(wall, TimeZone.getDefault());
        lastWallTick = wall; lastMonoTick = mono;
        manifest.put("schemaVersion", SCHEMA).put("nextPart",part).put("nextSeq",seq);
        save();
        raw = new FileOutputStream(new File(dir, name(part)+".open"), true);
    }
    public static String name(int part) { return String.format(Locale.US,"part_%06d",part); }
    public static long nextHour(long wall, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(wall); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        c.add(Calendar.HOUR_OF_DAY,1); return c.getTimeInMillis();
    }
    public static List<File> descriptors(File dir) {
        File[] files = dir.listFiles((d,n) -> n.matches("part_[0-9]{6}\\.meta\\.json"));
        List<File> out = new ArrayList<>();
        if (files != null) Collections.addAll(out,files);
        out.sort(Comparator.comparing(File::getName)); return out;
    }
    private void recoverJournal(File file) throws Exception {
        long validBytes = 0;
        try (RandomAccessFile r = new RandomAccessFile(file,"rw")) {
            String line;
            while ((line = r.readLine()) != null) {
                long end = r.getFilePointer();
                // readLine also returns an unterminated final line: never accept that tail.
                r.seek(end-1); boolean terminated = r.read() == '\n'; r.seek(end);
                if (!terminated) break;
                try {
                    JSONObject event = new JSONObject(new String(line.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8));
                    long n = event.getLong("seq");
                    if (n != seq) break;
                    include(event.getLong("wallMs"),event.getLong("monoNs"),n);
                    seq++; validBytes=end;
                } catch (Exception invalid) { break; }
            }
            r.setLength(validBytes);
        }
    }
    private void include(long wall,long mono,long n) {
        if (count == 0) { firstSeq=n; firstWall=wall; firstMono=mono; }
        count++; lastWall=wall; lastMono=mono;
    }
    public void append(String stream, JSONObject data, long wall, long mono) throws Exception {
        if (raw == null) throw new IOException("dataset_closed");
        if (raw.getChannel().size() >= partLimit) seal("size",false);
        JSONObject e = new JSONObject().put("schemaVersion",SCHEMA).put("sessionId",manifest.getString("id"))
                .put("seq",seq).put("wallMs",wall).put("monoNs",mono)
                .put("clockEpoch",manifest.optString("clockEpoch","initial")).put("stream",stream).put("data",data);
        byte[] bytes=(e.toString()+"\n").getBytes(StandardCharsets.UTF_8);
        raw.write(bytes); // No Java-side buffering: a killed process loses at most a partial last line.
        include(wall,mono,seq++);
    }
    /** Called on the writer thread, not the Android UI thread. */
    public boolean tick(long wall,long mono) throws Exception {
        long drift = (wall-lastWallTick) - (mono-lastMonoTick)/1_000_000L;
        boolean zoneChange=!zoneId.equals(TimeZone.getDefault().getID());
        zoneId=TimeZone.getDefault().getID();
        boolean clockChange = Math.abs(drift)>5_000L || zoneChange;
        if (clockChange) append("clock_change",new JSONObject().put("driftMs",drift).put("zoneChanged",zoneChange).put("timezone",zoneId),wall,mono);
        boolean due = wall >= nextHour || clockChange;
        if (due) { seal(clockChange?"clock_change":"hour",true); nextHour=nextHour(wall,TimeZone.getDefault()); }
        raw.getFD().sync();
        lastWallTick=wall; lastMonoTick=mono;
        manifest.put("lastWallMs",wall).put("lastMonoNs",mono).put("nextHourMs",nextHour);
        save(); return due;
    }
    public void seal(String reason,boolean release) throws Exception {
        if (count > 0) {
            raw.getFD().sync(); raw.close(); raw=null;
            File input=new File(dir,name(part)+".open"), out=new File(dir,name(part)+".jsonl.gz");
            File tmp=new File(dir,name(part)+".jsonl.gz.tmp");
            try(FileOutputStream f=new FileOutputStream(tmp); GZIPOutputStream g=new GZIPOutputStream(f,64*1024); InputStream in=new FileInputStream(input)) {
                byte[] buffer=new byte[64*1024]; int n;
                while((n=in.read(buffer))!=-1)g.write(buffer,0,n);
                g.finish(); f.getFD().sync();
            }
            move(tmp,out);
            JSONObject d=new JSONObject().put("part",part).put("file",out.getName())
                    .put("firstSeq",firstSeq).put("lastSeq",seq-1).put("eventCount",count)
                    .put("firstWallMs",firstWall).put("lastWallMs",lastWall)
                    .put("firstMonoNs",firstMono).put("lastMonoNs",lastMono)
                    .put("bytes",out.length()).put("sha256",sha256(out)).put("reason",reason);
            atomicJson(new File(dir,name(part)+".meta.json"),d);
            Files.deleteIfExists(input.toPath());
            part++; count=0;
            raw=new FileOutputStream(new File(dir,name(part)+".open"),true);
        }
        if (release) manifest.put("releasedThrough",part-1);
        manifest.put("nextPart",part).put("nextSeq",seq); save();
    }
    public void finish(String reason,long wall,long mono) throws Exception {
        append("session_end",new JSONObject().put("reason",reason),wall,mono);
        seal("final",true);
        manifest.put("state","closed").put("endWallMs",wall).put("endMonoNs",mono)
                .put("endReason",reason).put("partCount",part).put("eventCount",seq);
        save(); close();
        Files.deleteIfExists(new File(dir,name(part)+".open").toPath());
    }
    public void update(String key,Object value) throws Exception { manifest.put(key,value); save(); }
    public JSONObject snapshot() throws Exception { return new JSONObject(manifest.toString()); }
    private void save() throws Exception { atomicJson(new File(dir,"manifest.json"),manifest); }
    @Override public void close() throws IOException { if(raw!=null){raw.getFD().sync();raw.close();raw=null;} }
    public static JSONObject readJson(File file) throws Exception { return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)); }
    public static void atomicJson(File file,JSONObject json) throws Exception {
        File tmp=new File(file.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(json.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        move(tmp,file);
    }
    private static void move(File from,File to) throws IOException {
        try { Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException e){Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    public static String sha256(File f) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();
    }
}
