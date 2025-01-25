import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SocketListener
        implements Runnable
{
    protected InputStream in;
    protected BufferedReader reader;
    protected List<ChangeListener> listeners;

    public SocketListener( InputStream in )
    {
        this.in = in;
        this.listeners = new ArrayList<ChangeListener>();
        this.reader = new BufferedReader(new InputStreamReader(in));
    }

    public void addChangeListener( ChangeListener l )
    {
        this.listeners.add( l );
    }

    public void run()
    {
        while(true) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            String data;
            try {
                data = reader.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //need some more checking here to make sure 256 bytes was read, etc.
            //Maybe write a subclass of ChangeEvent
            ChangeEvent evt = new ChangeEvent(data);
            for (ChangeListener l : listeners) {
                l.stateChanged(evt);
            }
        }
    }
}