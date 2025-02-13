package pl.ks.profiling.safepoint.analyzer.commons.shared.gc.parser;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter(AccessLevel.PACKAGE)
public class GCAllocationStats {
    /**
     * Total size of objects managed by the GC.
     */
    private BigDecimal totalAllocation;

    /**
     * Total size of the heap at initilisation
     */
    private Integer initialHeapSize;

    /**
     * Maximum size of the heap ever
     */
    private Integer maxHeapSize;

    /**
     * Initial heap size occupance
     */
    private Integer initialHeapSizeOccupance;

    /**
     * Maximum heap size occupance
     */
    private Integer maxHeapSizeOccupance;
}
