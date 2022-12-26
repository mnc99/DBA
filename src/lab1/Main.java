
package lab1;

import agents.SSD;
import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Main {
    
    static String suffix = getHexaKey(4);

    public static void main(String[] args) {
        
        
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.loadAgent("Dookoo"+suffix, ITT_FULL.class);
        boot.loadAgent("SSD" + suffix, SSD.class);
        boot.WaitToShutDown();
    }
    
}
