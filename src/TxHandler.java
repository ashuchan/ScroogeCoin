import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
	
	private UTXOPool unspentTransactions;
	
	private UTXOPool spentTransactions;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
    		unspentTransactions = new UTXOPool(utxoPool);
    		spentTransactions = new UTXOPool();
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
	public synchronized boolean isValidTx(Transaction tx) {
		double inputValue = 0;
		double outputValue = 0;
		for(int i=0;i<tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			//4
			if (out.value < 0) {
				System.out.println("Output Coin value can not be less than 0");
				return false;
			}
			if (out.address==null) {
				System.out.println("Output Coin address can not be null");
				return false;
			}
			outputValue += out.value;
		}
		Set<UTXO> txPrevTxs = new HashSet<>();
		for (int i = 0; i < tx.numInputs(); i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO dummy = new UTXO(in.prevTxHash, in.outputIndex);
			//3
			if(txPrevTxs.contains(dummy)) {
				System.out.println("The transaction claims one coin more than once " + dummy);
				return false;
			} else {
				txPrevTxs.add(dummy);
			}
			//1
			if (!unspentTransactions.contains(dummy)) {
				if (spentTransactions.contains(dummy)) {
					System.out.println("Referred previous transaction is already spent " + dummy);
				} else {
					System.out.println("Referred previous transaction is not a valid transaction " + dummy);
				}
				return false;
			}
			/**
			 * Signature verification requires 3 inputs:
			 * 1. Signing key which will be recipient address from previous transaction
			 * 2. Data which is signed, which would be the rawDataToSign of the transaction in question
			 * 3. Signature attached to transaction, which is part of the input object in transaction
			 */
			//2
			if (!Crypto.verifySignature(unspentTransactions.getTxOutput(dummy).address, tx.getRawDataToSign(i), in.signature)) {
				System.out.println("Unable to verify signature for one of the referred transactions " + dummy);
				return false;
			}
			//Input Value will be output value of previous Transaction
			inputValue += unspentTransactions.getTxOutput(dummy).value;
		}
		//5
		if (inputValue<=outputValue) {
			System.out.println("Invalid input values for transaction. Output value can not be greater than input value");
			return false;
		}
		return true;
	}

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
	public synchronized Transaction[] handleTxs(Transaction[] possibleTxs) {
		List<Transaction> validTxs = new ArrayList<>();
		for (Transaction tx : possibleTxs) {
			if (isValidTx(tx)) {
				tx.finalize();
				// Mark all utilized transactions in this one as spent
				for (int i = 0; i < tx.numInputs(); i++) {
					Transaction.Input in = tx.getInput(i);
					UTXO old = new UTXO(in.prevTxHash, in.outputIndex);
					spentTransactions.addUTXO(old, unspentTransactions.getTxOutput(old));
					unspentTransactions.removeUTXO(old);
				}
				for (int i = 0; i < tx.numOutputs(); i++) {
					UTXO newUtx = new UTXO(tx.getHash(), i);
					unspentTransactions.addUTXO(newUtx, tx.getOutput(i));
				}
				validTxs.add(tx);
			}
		}
		Transaction[] output = new Transaction[validTxs.size()];
		int i =0;
		for(Transaction tx:validTxs) {
			output[i++] = tx;
		}
		return output;
	}

}
