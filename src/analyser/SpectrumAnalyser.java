package analyser;

import ddf.minim.AudioListener;
import ddf.minim.AudioSource;
import ddf.minim.Playable;
import ddf.minim.analysis.FFT;
import ddf.minim.analysis.HannWindow;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for analysing the spectrum of an audio stream.
 * 
 * @author  Stefan Marks
 * @version 1.0 - 12.05.2013: Created
 */
public class SpectrumAnalyser implements AudioListener
{
    /**
     * Listener class for notifications when a new sample has been analysed.
     */
    public interface Listener
    {
        void analysisUpdated(SpectrumAnalyser analyser);
    };
    
    
    /**
     * Creates a new Spectrum Analyser instance.
     * 
     * @param analysisFrequency  the frequency in Hz for analysing the waveforms
     * @param minFrequency       the minimum frequency to analyse
     * @param bandsPerOctave     the amount of analysis steps per octave
     * @param historySize        the size of the spectrum history
     */
    public SpectrumAnalyser(int analysisFrequency, int minFrequency, int bandsPerOctave, int historySize)
    {
        this.analyseFrequency = analysisFrequency;
        this.minFrequency     = minFrequency;
        this.bandsPerOctave   = bandsPerOctave;
        
        audioSource = null;
        dataRawL = dataRawR = null; 
        fft = null;
        
        history = new SpectrumInfo[historySize];
        for ( int i = 0 ; i < history.length ; i++ )
        {
            history[i] = new SpectrumInfo();
        }
        historyIdx = 0;
        
        listeners = new HashSet<Listener>();
    }
    
    
    /**
     * Resets the spectrum history.
     */
    private void resetHistory()
    {
        for ( SpectrumInfo info : history )
        {
            info.reset();
        }
        historyIdx = 0;
    }
    
    
    /**
     * Attaches the spectrum analyser to an audio stream.
     * 
     * @param as  the audio stream to attach to
     */
    public void attachToAudio(AudioSource as)
    {
        audioSource = as;
        
        float rate = audioSource.sampleRate();
        float minFreq = 20;
        // calculate minimum FFT buffer size 
        // to reliably measure a whole phase of a specific minimum frequency
        int minFftBufferSize = 1 << (int) (Math.log(rate / minFreq) / Math.log(2));
        // then add the sample buffer size
        int inputBufferSize = minFftBufferSize + audioSource.bufferSize();
        LOG.log(Level.INFO, 
                "Attached to sound source (Sample Rate {0}, Playback buffer size {1}, FFT buffer size {2}, Total buffer size {3})", 
                new Object[] {rate, audioSource.bufferSize(), minFftBufferSize, inputBufferSize});
        
        dataRawL = new float[inputBufferSize];
        dataRawR = new float[inputBufferSize];
        dataFftL = new float[minFftBufferSize];
        dataFftR = new float[minFftBufferSize];
        // data enters from the end of the buffer, so put index at end
        dataIdx = inputBufferSize; 
        // calculate sample steps for desired analysis frequency
        dataIdxStep = (int) (audioSource.sampleRate() / analyseFrequency);
                
        fft = new MaxFFT(minFftBufferSize, rate);
        fft.logAverages(minFrequency, bandsPerOctave);
        fft.window(new HannWindow());
        shaper = SpectrumShaper.LOGARITHMIC;

        // ready to go -> attach listener
        audioSource.addListener(this);
    }
    
    
    /**
     * Gets the spectrum shaper.
     * 
     * @return the spectrum shaper
     */
    public SpectrumShaper getSpectrumShaper()
    {
        return shaper;
    }
    
    
    /**
     * Sets the spectrum shaper.
     * 
     * @param shaper  the new spectrum shaper
     */
    public void setSpectrumShaper(SpectrumShaper shaper)
    {
        this.shaper = shaper;
    }
    
    
    /**
     * Checks if the analyser is attached to an audio stream.
     * 
     * @return <code>true</code> if the analyser is attached to an audio stream,
     *         <code>false</code> if not
     */
    public boolean isAttachedToAudio()
    {
        return fft != null;
    }
    
    
    /**
     * Detaches the spectrum analyser from the audio source.
     */
    public void detachFromAudio()
    {
        if ( audioSource != null )
        {
            ((AudioSource) audioSource).removeListener(this);  
            fft = null;
            audioSource = null;
            resetHistory();
        }
    }
    
    
    /**
     * Registers a new analysis result listener.
     * 
     * @param l  the analysis result listener to register.
     * @return <code>true</code> if the analysis result listener was successfully registered,
     *         <code>false</code> if not
     */
    public boolean registerListener(Listener l)
    {
        return listeners.add(l);
    }
    
    
    /**
     * Unregisters an analysis result listener.
     * 
     * @param l  the analysis result listener to unregister.
     * @return <code>true</code> if the analysis result listener was successfully unregistered,
     *         <code>false</code> if not
     */
    public boolean unregisterListener(Listener l)
    {
        return listeners.remove(l);
    }
    
    
    @Override
    public void samples(float[] samp)
    {
        samples(samp, samp);
    }

    
    @Override
    public void samples(float[] sampL, float[] sampR)
    {
        if ( audioSource == null ) return;
        
        Playable playable = null;
        if ( audioSource instanceof Playable )
        {
            playable = (Playable) audioSource;
            if ( !playable.isPlaying() ) return;
        }
        
        // shift data in input arrays
        System.arraycopy(dataRawL, sampL.length, dataRawL, 0, dataRawL.length - sampL.length);
        System.arraycopy(dataRawR, sampR.length, dataRawR, 0, dataRawR.length - sampR.length);
        // copy samples into array for analysis
        System.arraycopy(sampL, 0, dataRawL, dataRawL.length - sampL.length, sampL.length); 
        System.arraycopy(sampR, 0, dataRawR, dataRawR.length - sampR.length, sampR.length); 
        // move back the analysis index
        dataIdx -= sampL.length;
        
        // process as much data as possible
        while ( dataIdx + fft.timeSize() <= dataRawL.length )
        {
            // copy samples array into FFT array so values can be shaped by the windows
            // without destroying the original samples
            System.arraycopy(dataRawL, dataIdx, dataFftL, 0, dataFftL.length);
            // System.arraycopy(dataRawR, dataIdx, dataFftR, 0, dataFftR.length); 
            
            // do FFT
            fft.forward(dataFftL);

            // enter dataset into history
            synchronized(history)
            {      
                // calculate analysis offset to current playback position
                int   posOffset = (int) ((dataRawL.length - dataIdx) / audioSource.sampleRate() * 1000);
                int   posIdx    = (playable != null) ? playable.position() - posOffset : 0;
                float posRel    = (playable != null) ? (float) posIdx / (float) playable.length() : 0.0f;
                history[historyIdx].copySpectrumData(posIdx, posRel, this);
            }

            // move analysis window and history index forwards
            historyIdx = (historyIdx + 1) % history.length;
            dataIdx += dataIdxStep;

            // notify listeners
            for (Listener listener : listeners)
            {
               listener.analysisUpdated(this);
            }
        } 
    }
  
    
    /**
     * Gets the raw audio data for the left channel.
     * 
     * @return the raw left channel audio data
     */
    public float[] getAudioDataL()
    {
        return dataRawL;
    }
    
    
    /**
     * Gets the raw audio data for the right channel.
     * 
     * @return the raw right channel audio data
     */
    public float[] getAudioDataR()
    {
        return dataRawR;
    }
    
    
    /**
     * Gets the number of frequency bands the analyser returns.
     * 
     * @return the number of ferquency bands
     */
    public int getSpectrumBandCount()
    {
        return (fft != null) ? fft.avgSize() : 0;
    }
    
    
    /**
     * Gets the size of the spectrum history buffer.
     * 
     * @return the size of the frequency history buffer
     */
    public int getHistorySize()
    {
        return history.length;
    }
    
    
    /**
     * Gets the spectrum information for a specific position in history.
     * 
     * @param idx  the index of history (0: most recent)
     * @return the spectrum information 
     *         or <code>null</code> if there is no information
     */
    public SpectrumInfo getSpectrumInfo(int idx)
    {
        SpectrumInfo retInfo = null;
        
        if ( idx < history.length )
        {
            synchronized(history)
            {
                int absIdx = historyIdx - 1 - idx;
                if ( absIdx < 0 ) 
                { 
                    absIdx += history.length; 
                }
                retInfo = history[absIdx];
            }
            if ( !retInfo.isDefined() )
            {
                // not defined -> return null
                retInfo = null;
            }
        }
        return retInfo;
    }
    
    
    /**
     * Gets the FFT analyser.
     * 
     * @return the FFT analyser
     */
    public FFT getFFT()
    {
        return fft;
    }

    
    private AudioSource          audioSource;
    private float[]              dataRawL, dataRawR, dataFftL, dataFftR;
    private int                  dataIdx, dataIdxStep;
    private FFT                  fft;
    private SpectrumShaper       shaper;
    private final int            analyseFrequency, bandsPerOctave, minFrequency;
    private final SpectrumInfo[] history;
    private int                  historyIdx;
    private final Set<Listener>  listeners;

    private static final Logger LOG = Logger.getLogger(SpectrumAnalyser.class.getName());
}
