package pt.unl.fct.di.apdc.firstwebapp.util;

public class WorkSheetData {

    public String token;
    public String workReference;
    public String description;
    public String typeOfWorkTarget;
    public String awardStatus;
    public String awardDate;
    public String expectedStartDate;
    public String expectedCompletionDate;
    public String entityAccount;
    public String awardingEntity;
    public String companyTaxId;
    public String workStatus;
    public String notes;

    public WorkSheetData() {}

    public WorkSheetData(String token, String workReference, String description, String typeOfWorkTarget,
                         String awardStatus, String awardDate, String expectedStartDate,
                         String expectedCompletionDate, String entityAccount, String awardingEntity,
                         String companyTaxId, String workStatus, String notes) {
        this.token = token;
        this.workReference = workReference;
        this.description = description;
        this.typeOfWorkTarget = typeOfWorkTarget;
        this.awardStatus = awardStatus;
        this.awardDate = awardDate;
        this.expectedStartDate = expectedStartDate;
        this.expectedCompletionDate = expectedCompletionDate;
        this.entityAccount = entityAccount;
        this.awardingEntity = awardingEntity;
        this.companyTaxId = companyTaxId;
        this.workStatus = workStatus;
        this.notes = notes;
    }

    public boolean emptyOrBlankField(String field) {
        return field == null || field.isBlank();
    }

    public boolean anyEmptyOrBlank(String... fields) {
        for (String field : fields) {
            if (emptyOrBlankField(field)) {
                return true;
            }
        }
        return false;
    }

}
