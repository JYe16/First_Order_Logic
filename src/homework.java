/*
*
*   CSCI-561: HW3
*   Written by: Jackie Ye
*   To compile: javac homework.java
*
*/

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class homework {

    public static void main(String[] args) throws IOException {
        File inFile = new File("input.txt");
        Scanner sc = new Scanner(inFile);
        int qNum = sc.nextInt();
        String fol;
        String query;
        //to avoid empty string
        query = sc.nextLine();
        //declare an array of queries
        fol[] qArr = new fol[qNum];
        for (int i = 0; i < qNum; i++) {
            query = sc.nextLine();
            qArr[i] = getFunction(query);
            //negate the query
            qArr[i].not = !qArr[i].not;
        }
        int kbNum = sc.nextInt();
        //to avoid empty string
        fol = sc.nextLine();
        List<kb> kbList = new ArrayList<>();
        for (int i = 0; i < kbNum; i++) {
            fol = sc.nextLine();
            kbList.add(analyzeKB(fol));
        }
        FileWriter optWriter = new FileWriter("output.txt");
        //start asking the kb
        for (int i = 0; i < qArr.length; i++) {
            List<kb> checkList = new ArrayList<>();
            String result = Objects.requireNonNull(ask(qArr[i], kbList, checkList)).status;
            if (i != qArr.length - 1) {
                result += "\n";
            }
            optWriter.write(result);
            System.out.print(result);
        }
        optWriter.close();
    }

    static status ask(fol fol, List<kb> kbList, List<kb> checkList) {
        status found = findKB(fol, kbList);
        status result = new status();
        result.status = "FALSE";
        //System.out.println("Looking for: " + fol.func);

        if (found.status.equals("TRUE")) {
            List<kb> foundList = kbListCopy(found.list);
            for (kb kb : foundList) {
                //generate the name list first
                List<name> nameList = generateNameList(fol, kb);
                fol unifiedFOL = getUnifiedFOL(kb, fol);
                //if there is only 1 sentence in the kb
                if (kb.list.size() == 1) {
                    if (hasVariable(kb.list.get(0))) {
                        kb = replaceDownOnly(kb, nameList);
                    } else {
                        nameList.removeIf(name -> name.type == 0);
                        kb = replaceName(nameList, kb);
                    }
                } else {
                    nameList.removeIf(name -> name.type == 1);
                    kb = replaceName(nameList, kb);
                }
                kb = resolveKB(kb, fol);
                //if there is nothing in the list
                if (kb.list.isEmpty()) {
                    //asking complete, logic is satisfied
                    result.status = "TRUE";
                    result.nameList = nameList;
                    return result;
                }
                if (checkDeadLock(checkList, kb)) {
                    result.status = "FALSE";
                    break;
                } else {
                    checkList.add(kb);
                }
                boolean[] resultArr = new boolean[kb.list.size()];
                List<name> tempNameList = new ArrayList<>();
                for (int j = 0; j < kb.list.size(); j++) {
                    fol tempFOL = kb.list.get(j);
                    resultArr[j] = false;
                    status tempResult = ask(tempFOL, kbList, checkList);
                    if (tempResult.status.equals("FALSE")) {
                        result.status = "FALSE";
                        break;
                    } else {
                        result.status = "TRUE";
                        if (tempNameList.isEmpty()) {
                            tempNameList.addAll(tempResult.nameList);
                        } else {
                            for (name name : tempResult.nameList) {
                                if (name.type == 1) {
                                    if (checkNameList(name, tempNameList) == 1) {
                                        result.status = "FALSE";
                                        break;
                                    } else if (checkNameList(name, tempNameList) == 0) {
                                        tempNameList.add(name);
                                    }
                                }
                            }
                            if (result.status.equals("FALSE")) {
                                break;
                            }
                        }
                        resultArr[j] = true;
                    }
                }
                result.status = "TRUE";
                for (boolean tempBoolean : resultArr) {
                    if (!tempBoolean) {
                        result.status = "FALSE";
                        break;
                    }
                }
                if (result.status.equals("TRUE")) {
                    checkList.remove(kb);
                    if (unifiedFOL != null) {
                        unifiedFOL = folReplaceName(unifiedFOL, tempNameList);
                        result.nameList = genereateNewNameList(unifiedFOL, fol);
                    } else {
                        result.nameList = tempNameList;
                    }
                    break;
                }
            }
        }
        return result;
    }

    //This is for checking if a sentence has a variable or not
    static boolean hasVariable (fol fol) {
        for (String var: fol.var) {
            if (checkVariable(var)) {
                return true;
            }
        }
        return false;
    }

    static boolean checkDeadLock(List<kb> checkList, kb kb) {
        boolean result = false;
        for (kb temp : checkList) {
            result = true;
            if (temp.list.size() == kb.list.size()) {
                for (int i = 0; i < temp.list.size(); i++) {
                    if (temp.list.get(i).func.equals(kb.list.get(i).func) && temp.list.get(i).not == kb.list.get(i).not) {
                        for (int j = 0; j < temp.list.get(i).var.size(); j++) {
                            if (!temp.list.get(i).var.get(j).equals(kb.list.get(i).var.get(j))) {
                                result = false;
                            }
                        }
                    } else {
                        result = false;
                    }
                }
            } else {
                result = false;
            }
            if (result) {
                break;
            }
        }
        return result;
    }

    static List<name> genereateNewNameList(fol unified, fol original) {
        List<name> nameList = new ArrayList<>();
        for (int i = 0; i < unified.var.size(); i++) {
            if (!checkVariable(unified.var.get(i)) && checkVariable(original.var.get(i))) {
                name name = new name(original.var.get(i), unified.var.get(i), 1);
                nameList.add(name);
            }
        }
        return nameList;
    }

    static fol folReplaceName(fol fol, List<name> nameList) {
        for (String var : fol.var) {
            for (name name : nameList) {
                if (var.equals(name.original)) {
                    fol.setVar(name.replacement, var);
                }
            }
        }
        return fol;
    }

    static fol getUnifiedFOL(kb kb, fol fol) {
        for (fol temp : kb.list) {
            if (fol.func.equals(temp.func) && fol.not != temp.not) {
                return temp;
            }
        }
        return null;
    }

    static List<name> generateNameList(fol fol, kb kb) {
        List<name> nameList = new ArrayList<>();
        for (fol temp : kb.list) {
            if (fol.func.equals(temp.func) && fol.not != temp.not) {
                for (int i = 0; i < fol.var.size(); i++) {
                    //fol is not a variable
                    if (!checkVariable(fol.var.get(i)) && checkVariable(temp.var.get(i))) {
                        name name = new name(temp.var.get(i), fol.var.get(i), 0);
                        nameList.add(name);
                    } else if (checkVariable(fol.var.get(i)) && !checkVariable(temp.var.get(i))) {
                        name name = new name(fol.var.get(i), temp.var.get(i), 1);
                        nameList.add(name);
                    }
                }
            }
        }
        return nameList;
    }

    /*
     *
     *   Check for name list
     *   return:
     *   0: not found
     *   1: found but different (ERROR)
     *   2: found but same (should not add)
     *
     */
    static int checkNameList(name name, List<name> nameList) {
        for (name tempName : nameList) {
            if (name.original.equals(tempName.original)) {
                if (name.replacement.equals(tempName.replacement)) {
                    return 2;
                } else {
                    return 1;
                }
            } else if (name.replacement.equals(tempName.replacement)) {
                if (name.original.equals(tempName.original)) {
                    return 2;
                } else {
                    return 1;
                }
            }
        }
        return 0;
    }

    static kb resolveKB(kb kb, fol fol) {

        int pos = 0;
        for (fol temp : kb.list) {
            if (temp.func.equals(fol.func) && temp.not != fol.not) {
                pos = kb.list.indexOf(temp);
                break;
            }
        }
        kb.list.remove(pos);
        return kb;
    }

    static kb replaceName(List<name> nameList, kb kb) {
        for (fol fol : kb.list) {
            for (String n : fol.var) {
                for (name name : nameList) {
                    if (n.equals(name.original) && name.replacement != null) {
                        fol.setVar(name.replacement, n);
                    }
                }
            }
        }
        return kb;
    }

    static List<kb> kbListCopy(List<kb> kbList) {
        List<kb> resultList = new ArrayList<>();
        for (kb original : kbList) {
            kb result = kbCopy(original);
            resultList.add(result);
        }
        return resultList;
    }

    static kb kbCopy(kb original) {
        kb result = new kb();
        result.list = new ArrayList<>();
        for (fol temp : original.list) {
            fol toAdd = new fol();
            toAdd.func = temp.func;
            toAdd.not = temp.not;
            toAdd.var = new ArrayList<>();
            toAdd.var.addAll(temp.var);
            result.list.add(toAdd);
        }
        return result;
    }

    static status findKB(fol fol, List<kb> kbList) {
        status result = new status();
        result.list = new ArrayList<>();
        for (kb kb : kbList) {
            for (fol temp : kb.list) {
                //result.nameList.clear();
                boolean found = true;
                boolean[] constant = new boolean[fol.var.size()];
                //check the function name first
                if (fol.func.equals(temp.func)) {
                    for (int i = 0; i < fol.var.size(); i++) {
                        constant[i] = true;
                        //if both of vars are constants
                        if (!checkVariable(fol.var.get(i)) && !checkVariable(temp.var.get(i))) {
                            //they have to be equal
                            if (!fol.var.get(i).equals(temp.var.get(i))) {
                                found = false;
                            }
                        } else {
                            constant[i] = false;
                        }
                    }
                    if (found) {
                        result.status = "TRUE";
                        boolean x = true;
                        for (boolean tempBoolean : constant) {
                            if (!tempBoolean) {
                                x = false;
                                break;
                            }
                        }
                        //which means we found a complete fol
                        if (x && kb.list.size() == 1) {
                            if (fol.not == temp.not) {
                                result.status = "NEGATED";
                            } else {
                                result.list.clear();
                                result.list.add(kb);
                            }
                            return result;
                        } else if (fol.not != temp.not) {
                            result.list.add(kb);
                        }
                    }
                }
            }
        }
        if (result.list.isEmpty()) {
            result.status = "FALSE";
        }
        return result;
    }

    static kb replaceDownOnly (kb kb, List<name> nameList) {
        for (fol fol: kb.list) {
            for (String var: fol.var) {
                for (name name: nameList) {
                    if (name.type == 0 && var.equals(name.original)) {
                        fol.setVar(name.replacement, var);
                    }
                }
            }
        }
        return kb;
    }

    static boolean checkVariable(String name) {
        return name.length() == 1 && name.toLowerCase().equals(name);
    }

    static fol getFunction(String q) {
        int pos = 0;
        fol fol = new fol();
        fol.not = false;
        String temp;
        for (int i = 0; i < q.length(); i++) {
            if (q.charAt(i) == '~') {
                fol.not = true;
                pos += 1;
            }
            if (q.charAt(i) == '(') {
                temp = q.substring(pos, i);
                fol.func = temp;
                pos = i;
            }
            if (q.charAt(i) == ',') {
                temp = q.substring(pos + 1, i);
                fol.var.add(temp);
                pos = i;
            }
            if (q.charAt(i) == ')') {
                temp = q.substring(pos + 1, i);
                fol.var.add(temp);
                pos = i;
            }
        }
        return fol;
    }

    static kb analyzeKB(String kb) {
        kb k = new kb();
        int pos = 0;
        String sub;
        for (int i = 0; i < kb.length(); i++) {
            if (kb.charAt(i) == '&') {
                sub = kb.substring(pos, i - 1);
                fol temp = getFunction(sub);
                temp.not = !temp.not;
                k.list.add(temp);
                pos = i + 2;
            }
            if (kb.charAt(i) == '=') {
                sub = kb.substring(pos, i - 1);
                fol temp = getFunction(sub);
                temp.not = !temp.not;
                k.list.add(temp);
                sub = kb.substring(i + 3);
                temp = getFunction(sub);
                k.list.add(temp);
            }
        }
        if (k.list.isEmpty()) {
            fol temp = getFunction(kb);
            k.list.add(temp);
        }
        return k;
    }
}

class fol {
    String func;
    List<String> var = new ArrayList<>();
    boolean not;

    public fol() {
    }

    public void setVar(String newName, String original) {
        ListIterator<String> iterator = var.listIterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.equals(original)) {
                //Replace element
                iterator.set(newName);
            }
        }
    }
}

class kb {
    List<fol> list = new ArrayList<>();

    public kb() {
    }

}

class name {
    String original;
    String replacement;
    int type;

    public name(String original, String replacement, int type) {
        this.original = original;
        this.replacement = replacement;
        this.type = type;
    }
}

class status {
    String status;
    List<kb> list = new ArrayList<>();
    List<name> nameList = new ArrayList<>();

    public status() {
    }
}