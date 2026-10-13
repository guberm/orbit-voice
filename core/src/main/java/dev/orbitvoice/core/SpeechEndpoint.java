package dev.orbitvoice.core;
/** Lightweight watch endpoint; the phone's neural VAD decides whether a clip really holds speech.
 *  Measured on the watch: a quiet room floor is around rms 179 and real speech only reaches 150-500
 *  when the microphone is not close to the mouth, so a strict amplitude gate would reject most
 *  utterances. This gate therefore only rejects obvious silence and lets the phone's VAD judge. */
public final class SpeechEndpoint {
    private static final double FLOOR=120,MARGIN=1.15,RELEASE=1.05;
    private static final long IDLE=16000L*30,MAX=16000L*10;
    private static final long SILENCE=16000L*7/5;   // 1.4 s of quiet ends the utterance
    private static final int ONSET=1280;            // 80 ms above the gate starts it
    private boolean speech;private int voiced;private long silence,spoken,total,onset;private double noise=FLOOR,level;
    public boolean started(){return speech;}
    public double level(){return level;}
    public double floor(){return noise;}
    public long quiet(){return silence;}      // trailing quiet that ended the utterance
    public long onset(){return onset;}        // sample index where the utterance was detected
    public long total(){return total;}
    public double release(){return Math.max(FLOOR*3/4,noise*RELEASE);}
    public boolean accept(short[] samples,int count){
        double sum=0;for(int i=0;i<count;i++)sum+=(double)samples[i]*samples[i];level=Math.sqrt(sum/Math.max(1,count));
        total+=count;
        if(!speech){
            boolean sound=level>Math.max(FLOOR,noise*MARGIN);
            // Quiet speech dips below the gate between syllables; resetting here threw the whole
            // utterance away. Decay instead, so the onset accumulates over the last few hundred ms.
            if(sound)voiced+=count;else{voiced=Math.max(0,voiced-count);noise=noise*.97+level*.03;}
            if(voiced>=ONSET){speech=true;onset=total;}
            // Waiting silently must not last forever: the phone discards a clip that holds no speech.
            return !speech&&total>=IDLE;
        }
        spoken+=count;silence=level<Math.max(FLOOR*3/4,noise*RELEASE)?silence+count:0;
        return silence>=SILENCE||spoken>=MAX;
    }
}
