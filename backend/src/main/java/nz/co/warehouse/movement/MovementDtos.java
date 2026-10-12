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
                              @Valid List<IssueRequest> issues, @Positive Integer unitsPerCarton,
                              @PositiveOrZero int tailCartons,@Positive Long tailUnitsPerCarton,Long id,@Size(max=100) String subBatch,@Size(max=100) String materialBatch) {
        public ItemRequest(Long productId,String batchNo,int fullCartons,long looseUnits,Long totalUnits,boolean quantityUnknown,String remarks,List<IssueRequest> issues,Integer unitsPerCarton,int tailCartons,Long tailUnitsPerCarton,Long id,String subBatch){
            this(productId,batchNo,fullCartons,looseUnits,totalUnits,quantityUnknown,remarks,issues,unitsPerCarton,tailCartons,tailUnitsPerCarton,id,subBatch,null);
        }
        public ItemRequest(Long productId,String batchNo,int fullCartons,long looseUnits,Long totalUnits,boolean quantityUnknown,String remarks,List<IssueRequest> issues,Integer unitsPerCarton,int tailCartons,Long tailUnitsPerCarton,Long id){
            this(productId,batchNo,fullCartons,looseUnits,totalUnits,quantityUnknown,remarks,issues,unitsPerCarton,tailCartons,tailUnitsPerCarton,id,null);
        }
        public ItemRequest(Long productId,String batchNo,int fullCartons,long looseUnits,Long totalUnits,boolean quantityUnknown,String remarks,List<IssueRequest> issues){
            this(productId,batchNo,fullCartons,looseUnits,totalUnits,quantityUnknown,remarks,issues,null,0,null,null);
        }
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
                               boolean totalUnitsOverridden,boolean quantityUnknown,String remarks,List<IssueResponse> issues,int tailCartons,Long tailUnitsPerCarton,int totalCartons,String subBatch,String materialBatch) {}
    public record PhotoResponse(Long id,String url,String originalName,String mimeType,long fileSize,int width,int height,Instant createdAt) {}
    public record DetailResponse(Long id,String recordNo,MovementEnums.Direction direction,Instant movementTime,
                                 Long senderPersonId,String senderName,Long receiverPersonId,String receiverName,
                                 String manufactureLot,boolean returnMovement,Long productionTaskId,String productionTaskName,int totalCartons,String remarks,MovementEnums.Status status,
                                 String voidReason,Instant voidedAt,List<ItemResponse> items,List<PhotoResponse> photos,
                                 boolean hasIssues,boolean missingPhoto,Instant createdAt,Instant updatedAt,List<ListItemResponse> materials) {}
    public record ListResponse(Long id,String recordNo,MovementEnums.Direction direction,Instant movementTime,
                               String senderName,String receiverName,boolean returnMovement,int totalCartons,long totalQuantity,MovementEnums.Status status,
                               List<String> productNames,int itemCount,int photoCount,boolean hasIssues,boolean hasUnknownQuantity,List<ListItemResponse> items) {}
    // batchNo remains the material code; per-item materialBatch takes precedence over the legacy header.
    public record ListItemResponse(Long id,String productName,String materialCode,String materialBatch,String baseUnit,
                                   long fullCartons,long looseUnits,Long totalQuantity,boolean quantityUnknown,long tailCartons,long totalCartons,long unknownItemCount,List<MaterialQuantity.Packaging> packaging,String subBatch) {}
    public record PageResponse(List<ListResponse> content,int page,int size,long totalElements,int totalPages) {}
    public record StatMovementResponse(Long id,String recordNo,Instant movementTime,MovementEnums.Direction direction,
                                       String senderName,String receiverName,long fullCartons,long totalQuantity,long unknownItemCount,
                                       long looseUnits,boolean returnMovement,int photoCount,long tailCartons,long totalCartons,List<MaterialQuantity.Packaging> packaging) {}
    public record ProductStatResponse(String productName,String materialCode,String materialBatch,String baseUnit,
                                      long fullCartons,long looseUnits,long totalQuantity,long unknownItemCount,List<StatMovementResponse> movements,long tailCartons,long totalCartons,List<MaterialQuantity.Packaging> packaging,String subBatch) {}
    public record TodayStatsResponse(long totalCartons,long totalQuantity,long unknownItemCount,List<ProductStatResponse> products) {}
    public record DirectionStatResponse(MovementEnums.Direction direction,long totalCartons,long totalQuantity,long unknownItemCount,List<ProductStatResponse> products) {}
    public record QueryStatsResponse(List<DirectionStatResponse> directions) {}

    static DetailResponse detail(Movement m) {
        List<ItemResponse> items=m.getItems().stream().map(i->new ItemResponse(i.getId(),i.getProduct().canonicalId(),i.getProductNameSnapshot(),i.getSkuSnapshot(),i.getUnitsPerCartonSnapshot(),i.getBaseUnitSnapshot(),i.getBatchNo(),i.getFullCartons(),i.getLooseUnits(),i.getCalculatedTotalUnits(),i.getTotalUnits(),i.isTotalUnitsOverridden(),i.isQuantityUnknown(),i.getRemarks(),i.getIssues().stream().map(x->new IssueResponse(x.getId(),x.getIssueType(),x.getDescription())).toList(),i.getTailCartons(),i.getTailUnitsPerCarton(),i.getTotalCartons(),i.getSubBatch(),i.effectiveMaterialBatch())).toList();
        List<PhotoResponse> photos=m.getPhotos().stream().map(p->new PhotoResponse(p.getId(),"/api/movements/"+m.getId()+"/photos/"+p.getId()+"/content",p.getOriginalName(),p.getMimeType(),p.getFileSize(),p.getWidth(),p.getHeight(),p.getCreatedAt())).toList();
        boolean issues=items.stream().anyMatch(i->!i.issues().isEmpty());
        return new DetailResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.getMovementTime(),m.getSenderPerson().getId(),m.getSenderNameSnapshot(),m.getReceiverPerson().getId(),m.getReceiverNameSnapshot(),m.getManufactureLot(),m.isReturnMovement(),m.getProductionTask()==null?null:m.getProductionTask().getId(),m.getProductionTask()==null?null:m.getProductionTask().displayName(),m.getTotalCartons(),m.getRemarks(),m.getStatus(),m.getVoidReason(),m.getVoidedAt(),items,photos,issues,photos.isEmpty(),m.getCreatedAt(),m.getUpdatedAt(),materialSummaries(m));
    }
    static ListResponse summary(Movement m) {
        long totalQuantity=m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new ListResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.getMovementTime(),m.getSenderNameSnapshot(),m.getReceiverNameSnapshot(),m.isReturnMovement(),m.getTotalCartons(),totalQuantity,m.getStatus(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().limit(3).toList(),m.getItems().size(),m.getPhotos().size(),m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty()),m.getItems().stream().anyMatch(MovementItem::isQuantityUnknown),
                materialSummaries(m));
    }
    private static List<ListItemResponse> materialSummaries(Movement m){
        return MaterialQuantity.group(m.getItems()).values().stream().map(items->{
            MovementItem first=items.getFirst();MaterialQuantity totals=MaterialQuantity.of(items);
            boolean allUnknown=totals.unknownItemCount()==items.size();
            return new ListItemResponse(first.getId(),first.getProductNameSnapshot(),first.getBatchNo(),first.effectiveMaterialBatch(),first.getBaseUnitSnapshot(),
                    totals.fullCartons(),totals.looseUnits(),allUnknown?null:totals.totalQuantity(),allUnknown,totals.tailCartons(),totals.totalCartons(),totals.unknownItemCount(),totals.packaging(),first.getSubBatch());
        }).toList();
    }
}
