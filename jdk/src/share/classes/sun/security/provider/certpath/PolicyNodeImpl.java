/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.provider.certpath;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.security.cert.*;

/**
 * Implements the <code>PolicyNode</code> interface.
 * <p>
 * This class provides an implementation of the <code>PolicyNode</code>
 * interface, and is used internally to build and search Policy Trees.
 * While the implementation is mutable during construction, it is immutable
 * before returning to a client and no mutable public or protected methods
 * are exposed by this implementation, as per the contract of PolicyNode.
 *
 * @since       1.4
 * @author      Seth Proctor
 * @author      Sean Mullan
 */
final class PolicyNodeImpl implements PolicyNode {

    /**
     * Use to specify the special policy "Any Policy"
     */
    private static final String ANY_POLICY = "2.5.29.32.0";

    // every node has one parent, and zero or more children
    private PolicyNodeImpl mParent;
    private HashSet<PolicyNodeImpl> mChildren;

    // the 4 fields specified by RFC 3280
    private String mValidPolicy;
    private HashSet<PolicyQualifierInfo> mQualifierSet;
    private boolean mCriticalityIndicator;
    private HashSet<String> mExpectedPolicySet;
    private boolean mOriginalExpectedPolicySet;

    // the tree depth
    private int mDepth;
    // immutability flag
    private boolean isImmutable = false;

    /**
     * Constructor which takes a <code>PolicyNodeImpl</code> representing the
     * parent in the Policy Tree to this node. If null, this is the
     * root of the tree. The constructor also takes the associated data
     * for this node, as found in the certificate. It also takes a boolean
     * argument specifying whether this node is being created as a result
     * of policy mapping.
     *
     * @param parent the PolicyNode above this in the tree, or null if this
     *               node is the tree's root node
     * @param validPolicy a String representing this node's valid policy OID
     * @param qualifierSet the Set of qualifiers for this policy
     * @param criticalityIndicator a boolean representing whether or not the
     *                             extension is critical
     * @param expectedPolicySet a Set of expected policies
     * @param generatedByPolicyMapping a boolean indicating whether this
     * node was generated by a policy mapping
     */
    PolicyNodeImpl(PolicyNodeImpl parent, String validPolicy,
                Set<PolicyQualifierInfo> qualifierSet,
                boolean criticalityIndicator, Set<String> expectedPolicySet,
                boolean generatedByPolicyMapping) {
        mParent = parent;
        mChildren = new HashSet<PolicyNodeImpl>();

        if (validPolicy != null)
            mValidPolicy = validPolicy;
        else
            mValidPolicy = "";

        if (qualifierSet != null)
            mQualifierSet = new HashSet<PolicyQualifierInfo>(qualifierSet);
        else
            mQualifierSet = new HashSet<PolicyQualifierInfo>();

        mCriticalityIndicator = criticalityIndicator;

        if (expectedPolicySet != null)
            mExpectedPolicySet = new HashSet<String>(expectedPolicySet);
        else
            mExpectedPolicySet = new HashSet<String>();

        mOriginalExpectedPolicySet = !generatedByPolicyMapping;

        // see if we're the root, and act appropriately
        if (mParent != null) {
            mDepth = mParent.getDepth() + 1;
            mParent.addChild(this);
        } else {
            mDepth = 0;
        }
    }

    /**
     * Alternate constructor which makes a new node with the policy data
     * in an existing <code>PolicyNodeImpl</code>.
     *
     * @param parent a PolicyNode that's the new parent of the node, or
     *               null if this is the root node
     * @param node a PolicyNode containing the policy data to copy
     */
    PolicyNodeImpl(PolicyNodeImpl parent, PolicyNodeImpl node) {
        this(parent, node.mValidPolicy, node.mQualifierSet,
             node.mCriticalityIndicator, node.mExpectedPolicySet, false);
    }

    public PolicyNode getParent() {
        return mParent;
    }

    public Iterator<PolicyNodeImpl> getChildren() {
        return Collections.unmodifiableSet(mChildren).iterator();
    }

    public int getDepth() {
        return mDepth;
    }

    public String getValidPolicy() {
        return mValidPolicy;
    }

    public Set<PolicyQualifierInfo> getPolicyQualifiers() {
        return Collections.unmodifiableSet(mQualifierSet);
    }

    public Set<String> getExpectedPolicies() {
        return Collections.unmodifiableSet(mExpectedPolicySet);
    }

    public boolean isCritical() {
        return mCriticalityIndicator;
    }

    /**
     * Return a printable representation of the PolicyNode.
     * Starting at the node on which this method is called,
     * it recurses through the tree and prints out each node.
     *
     * @return a String describing the contents of the Policy Node
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer(this.asString());

        Iterator<PolicyNodeImpl> it = getChildren();
        while (it.hasNext()) {
            buffer.append(it.next());
        }
        return buffer.toString();
    }

    // private methods and package private operations

    boolean isImmutable() {
        return isImmutable;
    }

    /**
     * Sets the immutability flag of this node and all of its children
     * to true.
     */
    void setImmutable() {
        if (isImmutable)
            return;
        for (PolicyNodeImpl node : mChildren) {
            node.setImmutable();
        }
        isImmutable = true;
    }

    /**
     * Private method sets a child node. This is called from the child's
     * constructor.
     *
     * @param child new <code>PolicyNodeImpl</code> child node
     */
    private void addChild(PolicyNodeImpl child) {
        if (isImmutable) {
            throw new IllegalStateException("PolicyNode is immutable");
        }
        mChildren.add(child);
    }

    /**
     * Adds an expectedPolicy to the expected policy set.
     * If this is the original expected policy set initialized
     * by the constructor, then the expected policy set is cleared
     * before the expected policy is added.
     *
     * @param expectedPolicy a String representing an expected policy.
     */
    void addExpectedPolicy(String expectedPolicy) {
        if (isImmutable) {
            throw new IllegalStateException("PolicyNode is immutable");
        }
        if (mOriginalExpectedPolicySet) {
            mExpectedPolicySet.clear();
            mOriginalExpectedPolicySet = false;
        }
        mExpectedPolicySet.add(expectedPolicy);
    }

    /**
     * Removes all paths which don't reach the specified depth.
     *
     * @param depth an int representing the desired minimum depth of all paths
     */
    void prune(int depth) {
        if (isImmutable)
            throw new IllegalStateException("PolicyNode is immutable");

        // if we have no children, we can't prune below us...
        if (mChildren.size() == 0)
            return;

        Iterator<PolicyNodeImpl> it = mChildren.iterator();
        while (it.hasNext()) {
            PolicyNodeImpl node = it.next();
            node.prune(depth);
            // now that we've called prune on the child, see if we should
            // remove it from the tree
            if ((node.mChildren.size() == 0) && (depth > mDepth + 1))
                it.remove();
        }
    }

    /**
     * Deletes the specified child node of this node, if it exists.
     *
     * @param childNode the child node to be deleted
     */
    void deleteChild(PolicyNode childNode) {
        if (isImmutable) {
            throw new IllegalStateException("PolicyNode is immutable");
        }
        mChildren.remove(childNode);
    }

    /**
     * Returns a copy of the tree, without copying the policy-related data,
     * rooted at the node on which this was called.
     *
     * @return a copy of the tree
     */
    PolicyNodeImpl copyTree() {
        return copyTree(null);
    }

    private PolicyNodeImpl copyTree(PolicyNodeImpl parent) {
        PolicyNodeImpl newNode = new PolicyNodeImpl(parent, this);

        for (PolicyNodeImpl node : mChildren) {
            node.copyTree(newNode);
        }

        return newNode;
    }

    /**
     * Returns all nodes at the specified depth in the tree.
     *
     * @param depth an int representing the depth of the desired nodes
     * @return a <code>Set</code> of all nodes at the specified depth
     */
    Set<PolicyNodeImpl> getPolicyNodes(int depth) {
        Set<PolicyNodeImpl> set = new HashSet<PolicyNodeImpl>();
        getPolicyNodes(depth, set);
        return set;
    }

    /**
     * Add all nodes at depth depth to set and return the Set.
     * Internal recursion helper.
     */
    private void getPolicyNodes(int depth, Set<PolicyNodeImpl> set) {
        // if we've reached the desired depth, then return ourself
        if (mDepth == depth) {
            set.add(this);
        } else {
            for (PolicyNodeImpl node : mChildren) {
                node.getPolicyNodes(depth, set);
            }
        }
    }

    /**
     * Finds all nodes at the specified depth whose expected_policy_set
     * contains the specified expected OID (if matchAny is false)
     * or the special OID "any value" (if matchAny is true).
     *
     * @param depth an int representing the desired depth
     * @param expectedOID a String encoding the valid OID to match
     * @param matchAny a boolean indicating whether an expected_policy_set
     * containing ANY_POLICY should be considered a match
     * @return a Set of matched <code>PolicyNode</code>s
     */
    Set<PolicyNodeImpl> getPolicyNodesExpected(int depth,
        String expectedOID, boolean matchAny) {

        if (expectedOID.equals(ANY_POLICY)) {
            return getPolicyNodes(depth);
        } else {
            return getPolicyNodesExpectedHelper(depth, expectedOID, matchAny);
        }
    }

    private Set<PolicyNodeImpl> getPolicyNodesExpectedHelper(int depth,
        String expectedOID, boolean matchAny) {

        HashSet<PolicyNodeImpl> set = new HashSet<PolicyNodeImpl>();

        if (mDepth < depth) {
            for (PolicyNodeImpl node : mChildren) {
                set.addAll(node.getPolicyNodesExpectedHelper(depth,
                                                             expectedOID,
                                                             matchAny));
            }
        } else {
            if (matchAny) {
                if (mExpectedPolicySet.contains(ANY_POLICY))
                    set.add(this);
            } else {
                if (mExpectedPolicySet.contains(expectedOID))
                    set.add(this);
            }
        }

        return set;
    }

    /**
     * Finds all nodes at the specified depth that contains the
     * specified valid OID
     *
     * @param depth an int representing the desired depth
     * @param validOID a String encoding the valid OID to match
     * @return a Set of matched <code>PolicyNode</code>s
     */
    Set<PolicyNodeImpl> getPolicyNodesValid(int depth, String validOID) {
        HashSet<PolicyNodeImpl> set = new HashSet<PolicyNodeImpl>();

        if (mDepth < depth) {
            for (PolicyNodeImpl node : mChildren) {
                set.addAll(node.getPolicyNodesValid(depth, validOID));
            }
        } else {
            if (mValidPolicy.equals(validOID))
                set.add(this);
        }

        return set;
    }

    private static String policyToString(String oid) {
        if (oid.equals(ANY_POLICY)) {
            return "anyPolicy";
        } else {
            return oid;
        }
    }

    /**
     * Prints out some data on this node.
     */
    String asString() {
        if (mParent == null) {
            return "anyPolicy  ROOT\n";
        } else {
            StringBuffer sb = new StringBuffer();
            for (int i = 0, n = getDepth(); i < n; i++) {
                sb.append("  ");
            }
            sb.append(policyToString(getValidPolicy()));
            sb.append("  CRIT: ");
            sb.append(isCritical());
            sb.append("  EP: ");
            for (String policy : getExpectedPolicies()) {
                sb.append(policyToString(policy));
                sb.append(" ");
            }
            sb.append(" (");
            sb.append(getDepth());
            sb.append(")\n");
            return sb.toString();
        }
    }
}
