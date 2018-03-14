
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MaxFeeTxHandler {
	
	private UTXOPool unspentTransactions;
	
	private UTXOPool spentTransactions;
	
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
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
		for(int i=0;i<tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			if (out.value < 0) {
				System.out.println("Output Coin value can not be less than 0");
				return false;
			}
			if (out.address==null) {
				System.out.println("Output Coin address can not be null");
				return false;
			}
		}
		Set<UTXO> txPrevTxs = new HashSet<>();
		for (int i = 0; i < tx.numInputs(); i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO dummy = new UTXO(in.prevTxHash, in.outputIndex);
			if(txPrevTxs.contains(dummy)) {
				System.out.println("The transaction claims one coin more than once " + dummy);
				return false;
			} else {
				txPrevTxs.add(dummy);
			}
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
			if (!Crypto.verifySignature(unspentTransactions.getTxOutput(dummy).address, tx.getRawDataToSign(i), in.signature)) {
				System.out.println("Unable to verify signature for one of the referred transactions " + dummy);
				return false;
			}
		}
		return true;
	}
	
	public double calculateTransactionFee(Transaction tx) {
		double inputValue = 0;
		double outputValue = 0;
		for (int i = 0; i < tx.numInputs(); i++) {
			Transaction.Input in = tx.getInput(i);
			UTXO dummy = new UTXO(in.prevTxHash, in.outputIndex);
			inputValue += unspentTransactions.getTxOutput(dummy).value;
		}
		for(int i=0;i<tx.numOutputs();i++) {
			Transaction.Output out = tx.getOutput(i);
			outputValue += out.value;
		}
		return inputValue - outputValue;
	}

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public synchronized Transaction[] handleTxs(Transaction[] possibleTxs) {
    		List<Transaction> validTxs = new ArrayList<>();
    		Set<TransactionFeeWrapper> sortedTxs = new TreeSet<>();
    		for(Transaction tx:possibleTxs) {
    			if(isValidTx(tx)) {
    				double transactionFee = calculateTransactionFee(tx);
    				if (transactionFee>=0) {
    					sortedTxs.add(new TransactionFeeWrapper(tx, transactionFee));
    				}
    			}
    		}
    		
    		/**
    		 * We have a sorted set of transactions from which we 
    		 * start picking non clashing transactions in descending order
    		 */
    		for(TransactionFeeWrapper txw:sortedTxs) {
    			Transaction tx = txw.getTx();
    			boolean isStillValid = true;
    			for (int i = 0; i < tx.numInputs(); i++) {
    				Transaction.Input in = tx.getInput(i);
    				UTXO dummy = new UTXO(in.prevTxHash, in.outputIndex);
    				if (!unspentTransactions.contains(dummy)) {
    					isStillValid = false;
    				}
    			}
    			if(isStillValid) {
    				tx.finalize();
        			// Mark all utilized transactions in this one as spent
        			for(int i=0;i<tx.numInputs();i++) {
        				Transaction.Input in = tx.getInput(i);
        				UTXO old = new UTXO(in.prevTxHash, in.outputIndex);
        				spentTransactions.addUTXO(old, unspentTransactions.getTxOutput(old));
        				unspentTransactions.removeUTXO(old);
        			}
        			for(int i=0;i<tx.numOutputs();i++) {
        				UTXO newUtx = new UTXO(tx.getHash(), i);
        				unspentTransactions.addUTXO(newUtx, tx.getOutput(i));
        			}
    			}
    			validTxs.add(tx);
    		}
    		Transaction[] output = new Transaction[validTxs.size()];
    		int i =0;
    		for(Transaction tx:validTxs) {
    			output[i++] = tx;
    		}
    		return output;
    }
    
	public static class TransactionFeeWrapper implements Comparable<TransactionFeeWrapper>{
		private Transaction tx;
		private double transactionFee;

		public TransactionFeeWrapper(Transaction tx, double transactionFee) {
			super();
			this.tx = tx;
			this.transactionFee = transactionFee;
		}

		public Transaction getTx() {
			return tx;
		}

		public void setTx(Transaction tx) {
			this.tx = tx;
		}

		public double getTransactionFee() {
			return transactionFee;
		}

		public void setTransactionFee(double transactionFee) {
			this.transactionFee = transactionFee;
		}

		@Override
		public int compareTo(TransactionFeeWrapper o) {
			if (transactionFee - o.transactionFee < 0) {
				return -1;
			} else if (transactionFee - o.transactionFee > 0) {
				return 1;
			}
			return 0;
		}
	}

}
