package kr.co.antsnest.rtremote.computers;

import kr.co.antsnest.rtremote.nvstream.http.ComputerDetails;

public interface ComputerManagerListener {
    void notifyComputerUpdated(ComputerDetails details);
}
