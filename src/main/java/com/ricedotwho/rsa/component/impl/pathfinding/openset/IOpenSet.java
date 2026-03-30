package com.ricedotwho.rsa.component.impl.pathfinding.openset;

import com.ricedotwho.rsa.component.impl.pathfinding.PathNode;

public interface IOpenSet {
   void insert(PathNode var1);

   boolean isEmpty();

   PathNode removeLowest();

   void update(PathNode var1);
}
