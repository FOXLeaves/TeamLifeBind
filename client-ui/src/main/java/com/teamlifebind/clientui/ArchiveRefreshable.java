package com.teamlifebind.clientui;

/** Data normally advanced by native rendering must still advance while HTML owns the view. */
public interface ArchiveRefreshable {
    void tlb$refreshArchiveData();
}
