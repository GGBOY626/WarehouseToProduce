package nz.co.warehouse.production;

import nz.co.warehouse.product.ProductUsage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Independent of material readiness and the legacy production completion formula. */
public record FinishedReceiptForecast(String status, List<String> messages, String knownReceivedQuantity,
        PackedQuantity expected, PackedQuantity received, PackedQuantity remaining, PackedQuantity excess,
        BigDecimal progressPercent, boolean receivedIncomplete) {
    // Quantities are strings to preserve exact integers beyond JavaScript's safe integer range.
    public record PackedQuantity(String cartons, String boxes, String units, String fraction,
                                 String pallets, String palletCartons) {}

    static FinishedReceiptForecast calculate(ProductionTask task, ProductionTaskService.TargetResponse primary,
                                             long received, int unknownOutput) {
        var messages = new ArrayList<String>();
        boolean packed = task.outputCartonUnits() != null && task.outputCartonUnits() > 0;
        boolean configured = packed && task.getOutputProduct() != null && primary != null;
        if (!configured) messages.add("待配置：请选择成品、主料及成品箱规。");
        if (task.getOutputProduct() != null && task.getOutputProduct().canonical().getMovementDirection() != ProductUsage.PRODUCTION_TO_WAREHOUSE) {
            configured = false;
            messages.add("待配置：任务出货产品须属于成品分类。");
        }
        boolean inputKnown = primary != null && primary.materialTarget() && primary.unknownQuantityCount() == 0 && primary.completedQuantity() >= 0;
        if (configured && !inputKnown) messages.add("待核对：主料存在未知数量、负净发量或分类异常。");
        if (unknownOutput > 0) messages.add("待核对：关联成品回库含未知数量，已回库仅列已知部分，待回库与进度暂不计算。");
        BigInteger actual = BigInteger.valueOf(received);
        PackedQuantity actualPacked = packed ? pack(actual, BigInteger.ONE, task) : null;
        if (!configured || !inputKnown)
            return new FinishedReceiptForecast(configured ? "NEEDS_REVIEW" : "NEEDS_CONFIGURATION", messages,
                    actual.toString(), null, actualPacked, null, null, null, unknownOutput > 0);
        // One unit of net-issued primary material yields one finished inner unit.
        // BOX receipts count boxes, so convert through the saved units-per-box snapshot.
        BigInteger denominator = BigInteger.valueOf(task.getOutputQuantityBasis() == ProductionTask.OutputQuantityBasis.BOX
                ? task.getOutputUnitsPerBox() : 1);
        BigInteger expected = BigInteger.valueOf(primary.completedQuantity());
        BigInteger difference = expected.subtract(actual.multiply(denominator));
        BigDecimal progress = expected.signum() == 0 || unknownOutput > 0 ? null
                : new BigDecimal(actual.multiply(denominator).multiply(BigInteger.valueOf(100)))
                    .divide(new BigDecimal(expected), 2, RoundingMode.HALF_UP);
        if (difference.signum() < 0) messages.add("超额回库：已知回库数量已超过预计产量，请核对主料净发量和关联记录。");
        return new FinishedReceiptForecast(unknownOutput > 0 ? "NEEDS_REVIEW" : "READY", messages, actual.toString(),
                pack(expected, denominator, task), actualPacked,
                unknownOutput > 0 ? null : pack(difference.max(BigInteger.ZERO), denominator, task),
                difference.signum() < 0 ? pack(difference.negate(), denominator, task) : null, progress, unknownOutput > 0);
    }

    static PackedQuantity pack(BigInteger numerator, BigInteger denominator, ProductionTask task) {
        BigInteger[] whole = numerator.divideAndRemainder(denominator);
        BigInteger[] cartons = whole[0].divideAndRemainder(BigInteger.valueOf(task.outputCartonUnits()));
        int boxUnits = task.getOutputQuantityBasis() == ProductionTask.OutputQuantityBasis.BOX ? 1 : task.getOutputUnitsPerBox();
        BigInteger[] boxes = cartons[1].divideAndRemainder(BigInteger.valueOf(boxUnits));
        String fraction = null;
        if (whole[1].signum() != 0) {
            BigInteger gcd = whole[1].gcd(denominator);
            fraction = whole[1].divide(gcd) + "/" + denominator.divide(gcd);
        }
        BigInteger[] pallets = task.getOutputCartonsPerPallet() == null ? null
                : cartons[0].divideAndRemainder(BigInteger.valueOf(task.getOutputCartonsPerPallet()));
        return new PackedQuantity(cartons[0].toString(), boxes[0].toString(), boxes[1].toString(), fraction,
                pallets == null ? null : pallets[0].toString(), pallets == null ? null : pallets[1].toString());
    }
}
