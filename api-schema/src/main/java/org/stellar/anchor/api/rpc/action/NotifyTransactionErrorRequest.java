package org.stellar.anchor.api.rpc.action;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class NotifyTransactionErrorRequest extends RpcActionParamsRequest {

  private String id;
  private AmountRequest amount;

  @SerializedName("amount_fee")
  private AmountRequest amountFee;
}
