
package lab1;

import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Main {
    
    static String suffix = getHexaKey(4);

    public static void main(String[] args) {
        
        
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099);
        boot.launchAgent("Dookoo"+suffix, ITT_FULL.class);
        boot.WaitToShutDown();
    }
    
}
