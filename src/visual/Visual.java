package visual;

import modifier.Modifier;
import parameter.ParameterList;
import processing.core.PGraphics;
import timing.TimeBase;

/**
 * Interface for a visual element, e.g., an image or patterns.
 *
 * @author Stefan Marks
 */
public interface Visual
{
    /**
     * Gets the name of the visual
     * 
     * @return the name of the visual
     */
    public String getName();
    
    
    /**
     * Updates the visual per frame.
     * 
     * @param timeBase  the time base object
     */
    public void update(TimeBase timeBase);

    
    /**
     * Adds a modifier to a visual.
     * 
     * @param m the modifier to add
     * 
     * @return <code>true</code> if the modifier could be added,
     *         <code>false</code> if not
     */
    public boolean addModifier(Modifier m);

    
    /**
     * Gets the list of parameters of this visual.
     * 
     * @return the list of parameters of this visual
     */
    public ParameterList getParameters();

    
    /**
     * Checks if the visual is enabled.
     * 
     * @return <code>true</code> if it is enabled,
     *         <code>false</code> if not
     */
    public boolean isEnabled();
    
    
    /**
     * Enables or disables the visual.
     * 
     * @param enabled <code>true</code> to enable the visual,
     *                <code>false</code> to disable it
     */
    public void setEnabled(boolean enabled);
    

    /**
     * Renders the visual.
     * 
     * @param g the graphics context to render to
     */
    public void render(PGraphics g);
}
