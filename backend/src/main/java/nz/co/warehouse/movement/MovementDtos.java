package nz.co.warehouse.movement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class MovementDtos {
    private MovementDtos() {}

    public record IssueRequest(@NotNull MovementEnums.IssueType type,@Size(max=1000) String description) {}
    public record ItemRequest(@NotNull Long productId,@NotBlank @Size(max=100) String batchNo,
                              @PositiveOrZero int fullCartons,@PositiveOrZero long looseUnits,
                              @PositiveOrZero Long totalUnits,boolean quantityUnknown,@Size(max=1000) String remarks,
                              @Valid List<IssueRequest> issues) {
        public ItemRequest(Long productId,String batchNo,int fullCartons,long looseUnits,Long totalUnits,String remarks,List<IssueRequest> issues){
            this(productId,batchNo,fullCartons,looseUnits,totalUnits,false,remarks,issues);
        }
    }
    public record SaveRequest(@NotBlank @Size(max=36) String idempotencyKey,@NotNull MovementEnums.Direction direction,
                              @NotNull Instant movementTime,@NotNull Long senderPersonId,@NotNull Long receiverPersonId,
                              @Size(max=100) String manufactureLot,boolean returnMovement,Long productionTaskId,@Size(max=2000) String remarks,
                              @NotEmpty List<@Valid ItemRequest> items) {
        public SaveRequest(String idempotencyKey,MovementEnums.Direction direction,Instant movementTime,Long senderPersonId,Long receiverPersonId,
                           String manufactureLot,String remarks,List<ItemRequest> items){
            this(idempotencyKey,direction,movementTime,senderPersonId,receiverPersonId,manufactureLot,false,null,remarks,items);
        }
        public SaveRequest(String idempotencyKey,MovementEnums.Direction direction,Instant movementTime,Long senderPersonId,Long receiverPersonId,
                           String manufactureLot,boolean returnMovement,String remarks,List<ItemRequest> items){
            this(idempotencyKey,direction,movementTime,senderPersonId,receiverPersonId,manufactureLot,returnMovement,null,remarks,items);
        }
    }
    public record VoidRequest(@NotBlank @Size(max=500) String reason) {}

    public record IssueResponse(Long id,MovementEnums.IssueType type,String description) {}
    public record ItemResponse(Long id,Long productId,String productName,String sku,Integer unitsPerCarton,String baseUnit,
                               String batchNo,int fullCartons,long looseUnits,Long calculatedTotalUnits,Long totalUnits,
                               boolean totalUnitsOverridden,boolean quantityUnknown,String remarks,List<IssueResponse> issues) {}
    public record PhotoResponse(Long id,String url,String originalName,String mimeType,long fileSize,int width,int height,Instant createdAt) {}
    public record DetailResponse(Long id,String recordNo,MovementEnums.Direction direction,Instant movementTime,
                                 Long senderPersonId,String senderName,Long receiverPersonId,String receiverName,
                                 String manufactureLot,boolean returnMovement,Long productionTaskId,String productionTaskName,int totalCartons,String remarks,MovementEnums.Status status,
                                 String voidReason,Instant voidedAt,List<ItemResponse> items,List<PhotoResponse> photos,
                                 boolean hasIssues,boolean missingPhoto,Instant createdAt,Instant updatedAt) {}
    public record ListResponse(Long id,String recordNo,MovementEnums.Direction direction,Instant movementTime,
                               String senderName,String receiverName,boolean returnMovement,int totalCartons,long totalQuantity,MovementEnums.Status status,
                               List<String> productNames,int itemCount,int photoCount,boolean hasIssues,boolean hasUnknownQuantity) {}
    public record PageResponse(List<ListResponse> content,int page,int size,long totalElements,int totalPages) {}
    public record StatMovementResponse(Long id,String recordNo,Instant movementTime,MovementEnums.Direction direction,
                                       String senderName,String receiverName,long fullCartons,long totalQuantity,long unknownItemCount) {}
    public record ProductStatResponse(String productName,long fullCartons,long totalQuantity,long unknownItemCount,List<StatMovementResponse> movements) {
        public ProductStatResponse(String productName,long fullCartons,long totalQuantity,long unknownItemCount) {
            this(productName,fullCartons,totalQuantity,unknownItemCount,List.of());
        }
    }
    public record TodayStatsResponse(long totalCartons,long totalQuantity,long unknownItemCount,List<ProductStatResponse> products) {}
    public record DirectionStatResponse(MovementEnums.Direction direction,long totalCartons,long totalQuantity,long unknownItemCount,List<ProductStatResponse> products) {}
    public record QueryStatsResponse(List<DirectionStatResponse> directions) {}

    static DetailResponse detail(Movement m) {
        List<ItemResponse> items=m.getItems().stream().map(i->new ItemResponse(i.getId(),i.getProduct().getId(),i.getProductNameSnapshot(),i.getSkuSnapshot(),i.getUnitsPerCartonSnapshot(),i.getBaseUnitSnapshot(),i.getBatchNo(),i.getFullCartons(),i.getLooseUnits(),i.getCalculatedTotalUnits(),i.getTotalUnits(),i.isTotalUnitsOverridden(),i.isQuantityUnknown(),i.getRemarks(),i.getIssues().stream().map(x->new IssueResponse(x.getId(),x.getIssueType(),x.getDescription())).toList())).toList();
        List<PhotoResponse> photos=m.getPhotos().stream().map(p->new PhotoResponse(p.getId(),"/api/movements/"+m.getId()+"/photos/"+p.getId()+"/content",p.getOriginalName(),p.getMimeType(),p.getFileSize(),p.getWidth(),p.getHeight(),p.getCreatedAt())).toList();
        boolean issues=items.stream().anyMatch(i->!i.issues().isEmpty());
        return new DetailResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.getMovementTime(),m.getSenderPerson().getId(),m.getSenderNameSnapshot(),m.getReceiverPerson().getId(),m.getReceiverNameSnapshot(),m.getManufactureLot(),m.isReturnMovement(),m.getProductionTask()==null?null:m.getProductionTask().getId(),m.getProductionTask()==null?null:m.getProductionTask().getProduct().getName()+" · "+m.getProductionTask().getBatchNo(),m.getTotalCartons(),m.getRemarks(),m.getStatus(),m.getVoidReason(),m.getVoidedAt(),items,photos,issues,photos.isEmpty(),m.getCreatedAt(),m.getUpdatedAt());
    }
    static ListResponse summary(Movement m) {
        long totalQuantity=m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new ListResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.getMovementTime(),m.getSenderNameSnapshot(),m.getReceiverNameSnapshot(),m.isReturnMovement(),m.getTotalCartons(),totalQuantity,m.getStatus(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().limit(3).toList(),m.getItems().size(),m.getPhotos().size(),m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty()),m.getItems().stream().anyMatch(MovementItem::isQuantityUnknown));
    }
}
