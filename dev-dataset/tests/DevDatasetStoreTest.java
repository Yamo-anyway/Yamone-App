package com.yamo.snorelab;

import org.json.*;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class DevDatasetStoreTest {
    private File dir;private TimeZone old;
    private static final long START=1757982240000L; // transport logic is independent of the calendar date
    private JSONObject initial() throws Exception{return new JSONObject().put("id",dir.getName()).put("state","recording").put("releasedThrough",-1);}
    @Before public void setup() throws Exception{dir=Files.createTempDirectory("dataset-test").toFile();old=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));}
    @After public void cleanup() throws Exception{TimeZone.setDefault(old);try(java.util.stream.Stream<Path> s=Files.walk(dir.toPath())){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    private List<JSONObject> events() throws Exception{
        List<JSONObject> events=new ArrayList<>();int part=0;long seq=0;
        for(File f:DevDatasetStore.descriptors(dir)){
            JSONObject m=DevDatasetStore.readJson(f);assertEquals(part++,m.getInt("part"));File gz=new File(dir,m.getString("file"));
            assertEquals(m.getString("sha256"),DevDatasetStore.sha256(gz));long count=0;
            try(BufferedReader r=new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(gz)),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){JSONObject e=new JSONObject(line);assertEquals(seq++,e.getLong("seq"));events.add(e);count++;}}
            assertEquals(count,m.getLong("eventCount"));
        }return events;
    }
    @Test public void hourBoundaryUsesClockNotSessionStart() throws Exception{
        Calendar c=Calendar.getInstance();c.set(2026,8,16,9,24,0);c.set(Calendar.MILLISECOND,0);long t=c.getTimeInMillis();
        long next=DevDatasetStore.nextHour(t,TimeZone.getDefault());assertEquals(36*60000,next-t);
        DevDatasetStore s=new DevDatasetStore(dir,initial(),t,1);s.append("gps_raw",new JSONObject(),t,1);
        assertFalse(s.tick(next-1,1+(next-t-1)*1000000));assertEquals(0,DevDatasetStore.descriptors(dir).size());
        assertTrue(s.tick(next,1+(next-t)*1000000));assertEquals(0,s.snapshot().getInt("releasedThrough"));
        s.append("sensor",new JSONObject(),next+1000,1+(next-t+1000)*1000000);s.finish("switch_off",next+2000,1+(next-t+2000)*1000000);
        assertEquals(2,DevDatasetStore.descriptors(dir).size());assertEquals(3,events().size());
    }
    @Test public void earlySizePartsWaitForHourRelease() throws Exception{
        DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1,120);
        for(int i=0;i<8;i++)s.append("sensor",new JSONObject().put("v",i),START+i,1+i*1000000);
        assertTrue(DevDatasetStore.descriptors(dir).size()>1);assertEquals(-1,s.snapshot().getInt("releasedThrough"));
        s.finish("switch_off",START+100,100000001);assertEquals(9,events().size());
    }
    @Test public void crashRecoveryDiscardsOnlyIncompleteTail() throws Exception{
        DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.append("gps_raw",new JSONObject().put("한글","원본"),START,1);s.close();
        Files.write(new File(dir,"part_000000.open").toPath(),"{\"seq\":1,broken".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
        DevDatasetStore resumed=new DevDatasetStore(dir,null,START+10,10000001);resumed.append("process_restart",new JSONObject(),START+10,10000001);resumed.finish("stop",START+20,20000001);
        assertEquals(3,events().size());assertEquals("원본",events().get(0).getJSONObject("data").getString("한글"));
    }
    @Test public void descriptorWinsOverLeftoverJournal() throws Exception{
        DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.append("sensor",new JSONObject(),START,1);
        byte[] oldJournal=Files.readAllBytes(new File(dir,"part_000000.open").toPath());s.seal("hour",true);s.close();
        Files.write(new File(dir,"part_000000.open").toPath(),oldJournal);
        DevDatasetStore r=new DevDatasetStore(dir,null,START+100,100000001);r.finish("stop",START+100,100000001);
        assertEquals(2,events().size());assertFalse(new File(dir,"part_000000.open").exists());
    }
    @Test public void clockRollbackKeepsUniquePartsAndSequences() throws Exception{
        DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.append("sensor",new JSONObject(),START,1);
        assertTrue(s.tick(START-60000,1000000001));s.append("sensor",new JSONObject(),START-59000,2000000001L);s.finish("stop",START-58000,3000000001L);
        List<JSONObject> e=events();assertEquals(4,e.size());assertEquals("clock_change",e.get(1).getString("stream"));
    }
    @Test public void timezoneChangeReanchorsNextBoundary() throws Exception{
        DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.append("sensor",new JSONObject(),START,1);TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
        assertTrue(s.tick(START+1000,1000000001));assertEquals(DevDatasetStore.nextHour(START+1000,TimeZone.getDefault()),s.snapshot().getLong("nextHourMs"));s.finish("stop",START+2000,2000000001);
    }
    @Test public void finalWithoutSamplesStillHasEndEvent() throws Exception{DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.finish("stop",START+1,1000001);assertEquals(1,events().size());assertEquals(1,DevDatasetStore.readJson(new File(dir,"manifest.json")).getInt("partCount"));}
    @Test public void repeatedSealDoesNotCreateEmptyParts() throws Exception{DevDatasetStore s=new DevDatasetStore(dir,initial(),START,1);s.seal("hour",true);s.seal("hour",true);assertEquals(0,DevDatasetStore.descriptors(dir).size());s.finish("stop",START+1,1000001);assertEquals(1,events().size());}
}
