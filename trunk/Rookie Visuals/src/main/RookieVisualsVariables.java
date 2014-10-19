package main;

import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCParameter;
import com.illposed.osc.OSCPortIn;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Common variables for the Rookie Visuals that can be controlled through OSC.
 * 
 * @author  Stefan Marks
 */
public class RookieVisualsVariables 
{
    public RookieVisualsVariables()
    {
        paramList = new LinkedList<>();
        
        currentName = new OSCParameter<>("/name",      "");  paramList.add(currentName);
        logoVisible = new OSCParameter<>("/logo",    true);  paramList.add(logoVisible);
        curtainOpen = new OSCParameter<>("/curtain", false); paramList.add(curtainOpen);
    }

    
    public void registerWithInputPort(OSCPortIn port)
    {
        for ( OSCParameter param : paramList )
        {
            param.registerWithPort(port);
        }
    }

    
    public OSCPacket getFullPacket()
    {
        OSCBundle bundle = new OSCBundle();
        for ( OSCParameter param : paramList )
        {
            bundle.addPacket(param.prepareMessage());
        }
        return bundle;
    }
    
    
    public OSCPacket getUpdatePacket()
    {
        OSCBundle bundle = new OSCBundle();
        for ( OSCParameter param : paramList )
        {
            if ( param.isChanged() )
            {
                bundle.addPacket(param.prepareMessage());
                param.resetChanged();
            }
        }
        return bundle;
    }
    
    
    public OSCParameter findShaperVar(String name)
    {
        for ( OSCParameter param : paramList )
        {
            if ( param.getAddress().equals("/shaper/" + name) ) 
            {
                return param;
            }
        }
        return null;
    }
    
    
    public void writeToStream(PrintStream os)
    {
        for ( OSCParameter param : paramList )
        {
            String line = param.getAddress()+ " = " + param.valueToString();
            os.println(line);
        }
    }
    
    
    public void readFromStream(InputStream is) throws IOException
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        while ( br.ready() )
        {
            String   line  = br.readLine();
            String[] parts = line.split(" = ");
            if ( parts.length >= 2 )
            {
                for ( OSCParameter param : paramList )
                {
                    if ( param.getAddress().equals(parts[0]))
                    {
                        param.valueFromString(parts[1]);
                    }
                }
            }
        }
    }

    
    public OSCParameter<String>  currentName;
    public OSCParameter<Boolean> curtainOpen;
    public OSCParameter<Boolean> logoVisible;
    
    private final List<OSCParameter>  paramList;
}
