#simple hypergraph generator

import random

def main():
    print("hypergraph generator")

    #parameters
    outputFile = "output.txt"
    numNodes = 100
    numStartable = 10
    numHyperedges = 100

    #write number of nodes
    f = open(outputFile, "w")
    f.write(str(numNodes) + "\n")

    #write startable
    startable = range(numStartable)
    for s in startable:
        f.write(str(s) + ",")
    f.write("\n")

    #generate hyperedge by hops
    sourcePool = set(startable)
    for i in range(numHyperedges):
        sourceSetSize = random.randint(1, 4) #hyperedge source size 1~5
        sourceSet = set()
        while len(sourceSet) < sourceSetSize:
            idx = random.randint(0, len(sourcePool)-1)
            sourceList = list(sourcePool)
            sourceSet.add(sourceList[idx])
        targetNode = random.randint(0, numNodes)
        sourcePool.add(targetNode)

        #write edge
        for s in sourceSet:
            f.write(str(s) + ",")
        f.write(" -> " + str(targetNode) + "\n")

    f.close()

main()
