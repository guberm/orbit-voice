package dev.orbitvoice.core;
/** Lightweight watch endpoint; the phone's neural VAD validates speech before ASR.
 *  The watch microphone floor measures about -45 dBFS (rms 185), so the gate is relative
 *  to the running floor with a small margin, and idle listening is bounded instead of endless. */
public final class SpeechEndpoint {
    private static final double FLOOR=185,MARGIN=1.5;
    private static final long IDLE=16000L*30,SILENCE=12800L,MAX=16000L*28;
    private boolean speech;private int voiced;private long silence,spoken,total;private double noise=FLOOR,level;
    public boolean started(){return speech;}
    public double level(){return level;}
    public boolean accept(short[] samples,int count){
        double sum=0;for(int i=0;i<count;i++)sum+=(double)samples[i]*samples[i];level=Math.sqrt(sum/Math.max(1,count));
        total+=count;boolean sound=level>Math.max(FLOOR,noise*MARGIN);
        if(!speech){
            if(sound)voiced+=count;else{voiced=0;noise=noise*.97+level*.03;}
            if(voiced>=1600)speech=true;
            // Waiting silently must not last forever: the phone discards a clip that holds no speech.
            return !speech&&total>=IDLE;
        }
        spoken+=count;silence=sound?0:silence+count;
        return silence>=SILENCE||spoken>=MAX;
    }
}
